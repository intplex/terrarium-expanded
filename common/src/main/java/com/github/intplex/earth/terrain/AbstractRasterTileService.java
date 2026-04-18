package com.github.intplex.earth.terrain;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

abstract class AbstractRasterTileService<T> implements AutoCloseable {
    private final ExecutorService executor;
    private final RemotePngTileStore<T> store;

    protected AbstractRasterTileService(
        ExecutorService executor,
        RemotePngTileStore<T> store
    ) {
        this.executor = executor;
        this.store = store;
    }

    public final T requireTile(TileKey key) {
        return store.requireTile(key);
    }

    final T getOrLoad(TileKey key) {
        return store.getOrLoad(key);
    }

    protected static ExecutorService createDefaultExecutor() {
        return RemotePngTileStore.createDefaultExecutor(RemotePngTileStore.DEFAULT_IO_THREADS);
    }

    protected static ExecutorService createDefaultExecutor(int ioThreads) {
        return RemotePngTileStore.createDefaultExecutor(ioThreads);
    }

    final int configuredMemoryCacheEntries() {
        return store.memoryCacheEntries();
    }

    final int configuredPrefetchRadius() {
        return store.prefetchRadius();
    }

    final int configuredIoThreads() {
        if (executor instanceof ThreadPoolExecutor threadPoolExecutor) {
            return threadPoolExecutor.getMaximumPoolSize();
        }
        return -1;
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
