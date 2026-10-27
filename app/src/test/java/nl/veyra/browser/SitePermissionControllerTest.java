package nl.veyra.browser;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mozilla.geckoview.GeckoSession;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public final class SitePermissionControllerTest {
    private ActivityController<Activity> host;private SitePermissionController controller;
    private final AtomicBoolean granted=new AtomicBoolean(),rejected=new AtomicBoolean();
    private final GeckoSession.PermissionDelegate.Callback callback=new GeckoSession.PermissionDelegate.Callback(){public void grant(){granted.set(true);}public void reject(){rejected.set(true);}};
    @Before public void setup(){host=Robolectric.buildActivity(Activity.class).setup();controller=new SitePermissionController(host.get());}
    @After public void close(){controller.cancel();host.pause().stop().destroy();}
    @Test public void locationRequestsIncludeCoarseAndApproximatePermissionIsRespected(){
        controller.androidPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},callback,()->true);
        String[] requested=Shadows.shadowOf(host.get()).getLastRequestedPermission().requestedPermissions;
        assertTrue(Arrays.asList(requested).contains(Manifest.permission.ACCESS_FINE_LOCATION));assertTrue(Arrays.asList(requested).contains(Manifest.permission.ACCESS_COARSE_LOCATION));
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        controller.result(SitePermissionController.REQUEST,new int[]{PackageManager.PERMISSION_DENIED,PackageManager.PERMISSION_GRANTED});
        assertTrue(granted.get());assertFalse(rejected.get());
    }
    @Test public void staleWebsiteCannotReceivePermissionAfterAndroidDialogReturns(){
        AtomicBoolean valid=new AtomicBoolean(true);controller.androidPermissions(new String[]{Manifest.permission.CAMERA},callback,valid::get);
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA);valid.set(false);
        controller.result(SitePermissionController.REQUEST,new int[]{PackageManager.PERMISSION_GRANTED});assertTrue(rejected.get());assertFalse(granted.get());
    }
    @Test public void unsupportedAndroidPermissionsAreNeverRequested(){
        controller.androidPermissions(new String[]{Manifest.permission.READ_CONTACTS},callback,()->true);assertTrue(rejected.get());assertNull(Shadows.shadowOf(host.get()).getLastRequestedPermission());
    }
    @Test public void suspendingSiteDialogsDoesNotDiscardPendingAndroidResult(){
        controller.androidPermissions(new String[]{Manifest.permission.RECORD_AUDIO},callback,()->true);controller.suspend();
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO);
        controller.result(SitePermissionController.REQUEST,new int[]{PackageManager.PERMISSION_GRANTED});assertTrue(granted.get());
    }
}
