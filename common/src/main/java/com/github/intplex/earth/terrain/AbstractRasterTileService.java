package com.github.intplex.earth.terrain;

import java.util.concurrent.ExecutorService;
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
        return RemotePngTileStore.createDefaultExecutor();
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
