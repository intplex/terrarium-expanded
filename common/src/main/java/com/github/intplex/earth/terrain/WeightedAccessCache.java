package com.github.intplex.earth.terrain;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class WeightedAccessCache<K, V extends WeightedCacheValue> {
    private final long maxWeightBytes;
    private final long ttlNanos;
    private final LinkedHashMap<K, Entry<V>> entries;

    private long totalWeightBytes;

    WeightedAccessCache(long maxWeightBytes, int ttlSeconds) {
        this.maxWeightBytes = Math.max(1L, maxWeightBytes);
        this.ttlNanos = ttlSeconds > 0 ? ttlSeconds * 1_000_000_000L : 0L;
        this.entries = new LinkedHashMap<>(16, 0.75f, true);
        this.totalWeightBytes = 0L;
    }

    synchronized V getIfPresent(K key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            return null;
        }

        long now = System.nanoTime();
        if (isExpired(entry, now)) {
            removeEntry(key, entry);
            return null;
        }
        entry.lastAccessNanos = now;
        return entry.value;
    }

    synchronized void put(K key, V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");

        long now = System.nanoTime();
        evictExpired(now);

        Entry<V> replacement = new Entry<>(value, Math.max(1, value.estimatedBytes()), now);
        Entry<V> previous = entries.put(key, replacement);
        if (previous != null) {
            totalWeightBytes -= previous.weightBytes;
        }
        totalWeightBytes += replacement.weightBytes;
        evictToBudget();
    }

    synchronized void clear() {
        entries.clear();
        totalWeightBytes = 0L;
    }

    synchronized long weightedSizeBytes() {
        evictExpired(System.nanoTime());
        return Math.max(0L, totalWeightBytes);
    }

    long maxWeightBytes() {
        return maxWeightBytes;
    }

    private void evictExpired(long now) {
        if (ttlNanos <= 0L || entries.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<K, Entry<V>>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, Entry<V>> pair = iterator.next();
            Entry<V> entry = pair.getValue();
            if (!isExpired(entry, now)) {
                break;
            }
            totalWeightBytes -= entry.weightBytes;
            iterator.remove();
        }
    }

    private void evictToBudget() {
        if (entries.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<K, Entry<V>>> iterator = entries.entrySet().iterator();
        while (totalWeightBytes > maxWeightBytes && entries.size() > 1 && iterator.hasNext()) {
            Entry<V> eldest = iterator.next().getValue();
            totalWeightBytes -= eldest.weightBytes;
            iterator.remove();
        }
    }

    private boolean isExpired(Entry<V> entry, long now) {
        return ttlNanos > 0L && now - entry.lastAccessNanos >= ttlNanos;
    }

    private void removeEntry(K key, Entry<V> entry) {
        Entry<V> removed = entries.remove(key);
        if (removed != null) {
            totalWeightBytes -= removed.weightBytes;
        } else {
            totalWeightBytes -= entry.weightBytes;
        }
    }

    private static final class Entry<V> {
        private final V value;
        private final int weightBytes;
        private long lastAccessNanos;

        private Entry(V value, int weightBytes, long lastAccessNanos) {
            this.value = value;
            this.weightBytes = weightBytes;
            this.lastAccessNanos = lastAccessNanos;
        }
    }
}
