package com.github.intplex.earth.terrain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RemotePngTileStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void immediateCompletionSucceedsAndEvictedTilesReloadFromDisk() {
        AtomicInteger downloads = new AtomicInteger();
        RemotePngTileStore<TestTile> store = store(new ImmediateExecutor(), key -> {
            downloads.incrementAndGet();
            return new byte[] {1};
        }, (bytes, key) -> new TestTile(), 0);
        TileKey firstKey = new TileKey(1, 1);

        TestTile first = store.getOrLoad(firstKey);
        assertSame(first, store.getOrLoad(firstKey));
        store.getOrLoad(new TileKey(1, 2));
        assertNotSame(first, store.getOrLoad(firstKey));
        assertEquals(2, downloads.get(), "evicted tile should reload from disk, not a retained future");
    }

    @Test
    void failedFetchCanBeRetriedImmediately() {
        AtomicInteger downloads = new AtomicInteger();
        IOException failure = new IOException("temporary outage");
        RemotePngTileStore<TestTile> store = store(new ImmediateExecutor(), key -> {
            if (downloads.incrementAndGet() == 1) {
                throw failure;
            }
            return new byte[] {1};
        }, (bytes, key) -> new TestTile(), 0);
        TileKey key = new TileKey(1, 1);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> store.getOrLoad(key)).getCause());
        store.getOrLoad(key);
        assertEquals(2, downloads.get());
    }

    @Test
    void failedDecodeCanBeRetriedImmediately() {
        AtomicInteger decodes = new AtomicInteger();
        IllegalArgumentException failure = new IllegalArgumentException("invalid PNG");
        RemotePngTileStore<TestTile> store = store(new ImmediateExecutor(), key -> new byte[] {1}, (bytes, key) -> {
            if (decodes.incrementAndGet() == 1) {
                throw failure;
            }
            return new TestTile();
        }, 0);
        TileKey key = new TileKey(1, 1);

        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> store.getOrLoad(key)));
        store.getOrLoad(key);
        assertEquals(2, decodes.get());
    }

    @Test
    void rejectedSubmissionDoesNotLeaveAnUnfinishedFuture() {
        ImmediateExecutor executor = new ImmediateExecutor();
        executor.rejectNext = true;
        RemotePngTileStore<TestTile> store = store(executor, key -> new byte[] {1}, (bytes, key) -> new TestTile(), 0);
        TileKey key = new TileKey(1, 1);

        assertThrows(RejectedExecutionException.class, () -> store.getOrLoad(key));
        store.getOrLoad(key);
    }

    @Test
    void failedPrefetchDoesNotFailRequiredTileOrPoisonLaterDemand() {
        TileKey center = new TileKey(1, 1);
        TileKey neighbor = new TileKey(0, 0);
        AtomicInteger neighborDownloads = new AtomicInteger();
        RemotePngTileStore<TestTile> store = store(new ImmediateExecutor(), key -> {
            if (key.equals(neighbor) && neighborDownloads.incrementAndGet() == 1) {
                throw new IOException("temporary prefetch outage");
            }
            return new byte[] {1};
        }, (bytes, key) -> new TestTile(), 1);

        store.requireTile(center);
        store.getOrLoad(neighbor);
        assertEquals(2, neighborDownloads.get());
    }

    private RemotePngTileStore<TestTile> store(
        ImmediateExecutor executor,
        RemotePngTileStore.TileFetcher fetcher,
        RemotePngTileStore.TileDecoder<TestTile> decoder,
        int prefetchRadius
    ) {
        return new RemotePngTileStore<>(new RemotePngTileStore.StoreConfig<>(
            tempDir, executor, fetcher, decoder, key -> true, 1, 0, prefetchRadius
        ));
    }

    private static final class TestTile implements WeightedCacheValue {
        @Override
        public int estimatedBytes() {
            return 1;
        }
    }

    // Forces completion before whenComplete is attached, reproducing the race
    // deterministically without depending on thread scheduling or disk speed.
    private static final class ImmediateExecutor extends AbstractExecutorService {
        private boolean shutdown;
        private boolean rejectNext;

        @Override
        public void execute(Runnable command) {
            if (shutdown || rejectNext) {
                rejectNext = false;
                throw new RejectedExecutionException("synthetic rejection");
            }
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown();
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }
    }
}
