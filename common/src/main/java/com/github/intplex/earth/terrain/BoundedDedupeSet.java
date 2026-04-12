package com.github.intplex.earth.terrain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class BoundedDedupeSet<T> {
    private final int maxEntries;
    private final Map<T, Boolean> entries;

    BoundedDedupeSet(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<T, Boolean> eldest) {
                return size() > BoundedDedupeSet.this.maxEntries;
            }
        };
    }

    synchronized boolean contains(T key) {
        return entries.containsKey(key);
    }

    synchronized boolean markIfNew(T key) {
        Objects.requireNonNull(key);
        if (entries.containsKey(key)) {
            return false;
        }
        entries.put(key, Boolean.TRUE);
        return true;
    }

    synchronized void clear() {
        entries.clear();
    }
}
