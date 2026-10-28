package nl.veyra.browser;

import java.util.ArrayDeque;

/** Session-only undo for normal web tabs. Private tabs never enter this collection. */
final class RecentlyClosedTabs {
    static final int LIMIT = 5;
    private static long deletionGeneration;
    private long generation = deletionGeneration;
    private final ArrayDeque<TabStateStore.Entry> entries = new ArrayDeque<>();

    void remember(String url, String title, boolean home, boolean privateMode) {
        forgetDeletedHistory();
        if (privateMode || home) return;
        String normalized = UrlPolicy.normalizeWebUrl(url);
        if (normalized == null) return;
        entries.addFirst(new TabStateStore.Entry(normalized, title == null ? "" : title, false, false));
        while (entries.size() > LIMIT) entries.removeLast();
    }

    // All instances are accessed on the UI thread; no addresses enter the invalidation signal.
    static void historyDeleted() { deletionGeneration++; }
    private void forgetDeletedHistory() { if (generation != deletionGeneration) { entries.clear(); generation = deletionGeneration; } }
    boolean isEmpty() { forgetDeletedHistory(); return entries.isEmpty(); }
    TabStateStore.Entry take() { forgetDeletedHistory(); return entries.pollFirst(); }
    void clear() { entries.clear(); }
}
