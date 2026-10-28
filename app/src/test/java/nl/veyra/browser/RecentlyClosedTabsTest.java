package nl.veyra.browser;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RecentlyClosedTabsTest {
    @Test public void privateHomeAndUnsafeAddressesNeverBecomeReopenable() {
        RecentlyClosedTabs closed = new RecentlyClosedTabs();
        closed.remember("https://secret.example/", "Secret", false, true);
        closed.remember("https://example.org/", "Home", true, false);
        closed.remember("javascript:alert(1)", "Invalid", false, false);
        closed.remember(null, null, false, false);
        assertTrue(closed.isEmpty()); assertNull(closed.take());
    }
    @Test public void keepsOnlyTheFiveMostRecentNormalTabsInClosingOrder() {
        RecentlyClosedTabs closed = new RecentlyClosedTabs();
        for (int i=0;i<9;i++) closed.remember("https://example.org/"+i, "Page "+i, false, false);
        for (int i=8;i>=4;i--) {
            TabStateStore.Entry entry = closed.take();
            assertEquals("https://example.org/"+i, entry.url); assertEquals("Page "+i, entry.title);
            assertFalse(entry.privateMode); assertFalse(entry.home);
        }
        assertNull(closed.take()); assertTrue(closed.isEmpty());
    }
    @Test public void historyDeletionInvalidatesEveryLiveUndoStackBeforeReadsOrWrites() {
        RecentlyClosedTabs first=new RecentlyClosedTabs(),second=new RecentlyClosedTabs();
        first.remember("https://example.org/old", "Old", false, false);
        second.remember("https://example.org/old", "Old", false, false);
        RecentlyClosedTabs.historyDeleted();
        assertNull(first.take());
        second.remember("https://example.org/new", "New", false, false);
        assertEquals("https://example.org/new",second.take().url); assertTrue(second.isEmpty());
    }
    @Test public void clearingSessionUndoRemovesAllAddresses() {
        RecentlyClosedTabs closed = new RecentlyClosedTabs();
        closed.remember("https://example.org/", null, false, false);
        closed.clear(); assertTrue(closed.isEmpty()); assertNull(closed.take());
    }
}
