package cn.threatexpert.gonc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-directory, single-flight index. Only locations are cached, not live file metadata. */
final class ShareDirectoryIndex<T> {
    interface Loader<T> { List<T> load() throws Exception; }
    interface Name<T> { String of(T entry); }

    private final Loader<T> loader;
    private final Name<T> name;
    private Map<String, T> entries;

    ShareDirectoryIndex(Loader<T> loader, Name<T> name) {
        this.loader = loader;
        this.name = name;
    }

    synchronized T find(String key, boolean refresh) throws Exception {
        boolean loaded = refresh || entries == null;
        if (loaded) reload();
        T found = entries.get(key);
        // A newly added or renamed file may not be in the previous listing.
        if (found == null && !loaded) {
            reload();
            found = entries.get(key);
        }
        return found;
    }

    synchronized List<T> refresh() throws Exception {
        reload();
        return new ArrayList<>(entries.values());
    }

    private void reload() throws Exception {
        // Never retain a stale listing after a failed provider query.
        entries = null;
        Map<String, T> next = new LinkedHashMap<>();
        for (T entry : loader.load()) next.put(name.of(entry), entry);
        entries = next;
    }
}
