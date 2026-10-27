package nl.veyra.browser;

import android.net.Uri;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.WebExtension;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class BrowserEngineInstrumentedTest {
    @Test public void startsBundledProtectionAndRejectsUnsignedAddons() throws Exception {
        try (ActivityScenario<BrowserActivity> scenario = ActivityScenario.launch(BrowserActivity.class)) {
            AtomicReference<VeyraRuntime> runtime = new AtomicReference<>();
            scenario.onActivity(activity -> runtime.set(VeyraRuntime.get(activity)));
            await(runtime.get().ready);
            File file = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "unsigned-test.xpi");
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(("{\"manifest_version\":2,\"name\":\"Unsigned verification fixture\",\"version\":\"1.0\","
                        + "\"browser_specific_settings\":{\"gecko\":{\"id\":\"unsigned-test@veyra.invalid\"}}}").getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            AtomicReference<GeckoResult<WebExtension>> install = new AtomicReference<>();
            scenario.onActivity(activity -> install.set(runtime.get().runtime.getWebExtensionController().install(Uri.fromFile(file).toString())));
            try { await(install.get()); fail("Unsigned extension was accepted"); }
            catch (WebExtension.InstallException expected) {
                assertEquals(WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED, expected.code);
            } finally { assertTrue(file.delete()); }
        }
    }
    /** Optional real AMO fixture, supplied explicitly to an isolated test device. */
    @Test public void installsSignedAddonWithConsentAndManagesItsState() throws Exception {
        File fixture = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "signed-test-addon.xpi");
        org.junit.Assume.assumeTrue("Supply a signed AMO test fixture in the test app cache", fixture.isFile());
        try (ActivityScenario<BrowserActivity> scenario = ActivityScenario.launch(BrowserActivity.class)) {
            AtomicReference<VeyraRuntime> reference = new AtomicReference<>();
            scenario.onActivity(activity -> reference.set(VeyraRuntime.get(activity)));
            VeyraRuntime engine = reference.get(); await(engine.ready);
            java.util.Set<String> existing = new java.util.HashSet<>();
            for (WebExtension extension : await(engine.runtime.getWebExtensionController().list())) existing.add(extension.id);
            // The supplied fixture is ClearURLs. Preserve any pre-existing user installation.
            org.junit.Assume.assumeFalse(existing.contains("{74145f27-f039-47ce-a470-a662b129930a}"));
            AtomicReference<GeckoResult<WebExtension>> operation = new AtomicReference<>();
            scenario.onActivity(activity -> operation.set(engine.runtime.getWebExtensionController().install(Uri.fromFile(fixture).toString())));
            androidx.test.uiautomator.UiDevice device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            androidx.test.uiautomator.UiObject2 allow = device.wait(androidx.test.uiautomator.Until.findObject(
                    androidx.test.uiautomator.By.text("Toestaan")), 90000);
            assertNotNull("Signed installation must present the permission consent dialog", allow);
            allow.click();
            WebExtension installed = await(operation.get());
            try {
                assertFalse(installed.isBuiltIn);
                assertTrue(installed.metaData.enabled);
                scenario.onActivity(activity -> operation.set(engine.runtime.getWebExtensionController().disable(installed,
                        org.mozilla.geckoview.WebExtensionController.EnableSource.USER)));
                assertFalse(await(operation.get()).metaData.enabled);
                scenario.onActivity(activity -> operation.set(engine.runtime.getWebExtensionController().enable(installed,
                        org.mozilla.geckoview.WebExtensionController.EnableSource.USER)));
                assertTrue(await(operation.get()).metaData.enabled);
                scenario.recreate();
                boolean found = false;
                for (WebExtension extension : await(engine.runtime.getWebExtensionController().list()))
                    if (installed.id.equals(extension.id)) found = true;
                assertTrue("Installed add-ons must survive activity recreation", found);
            } finally {
                AtomicReference<GeckoResult<Void>> removal = new AtomicReference<>();
                scenario.onActivity(activity -> removal.set(engine.runtime.getWebExtensionController().uninstall(installed)));
                await(removal.get());
            }
        }
    }
    private static <T> T await(GeckoResult<T> result) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>(); AtomicReference<Throwable> error = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> result.accept(v -> {
            value.set(v); done.countDown();
        }, e -> { error.set(e); done.countDown(); }));
        assertTrue("Gecko operation timed out", done.await(150, TimeUnit.SECONDS));
        if (error.get() instanceof Exception) throw (Exception) error.get();
        if (error.get() != null) throw new AssertionError(error.get());
        return value.get();
    }
}
