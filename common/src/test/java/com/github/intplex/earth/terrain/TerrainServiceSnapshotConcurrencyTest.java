package com.github.intplex.earth.terrain;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainServiceSnapshotConcurrencyTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedBuildKeepsWaitersOnTheSameLock(boolean clearDuringRetry) throws Exception {
        TerrainService.ChunkSnapshotCache cache = new TerrainService.ChunkSnapshotCache(1024 * 1024, 0);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch failFirst = new CountDownLatch(1);
        CountDownLatch retryStarted = new CountDownLatch(1);
        CountDownLatch finishRetry = new CountDownLatch(1);
        AtomicInteger retryBuilds = new AtomicInteger();
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        AtomicReference<Thread> thirdThread = new AtomicReference<>();
        IllegalStateException failure = new IllegalStateException("temporary elevation outage");
        TerrainChunkSnapshot snapshot = new TerrainChunkSnapshot(
            0, 0, new long[4], new short[256], new short[256], new short[256],
            new long[4], new long[4], new byte[256], new float[256], new float[256], new float[256]
        );
        TerrainService.ChunkSnapshotBuilder retryBuilder = (x, z) -> {
            retryBuilds.incrementAndGet();
            retryStarted.countDown();
            await(finishRetry);
            return snapshot;
        };

        try (var callers = Executors.newFixedThreadPool(3)) {
            try {
                CompletableFuture<TerrainChunkSnapshot> first = CompletableFuture.supplyAsync(() -> cache.getOrBuildFor(0, 0, (x, z) -> {
                    firstStarted.countDown();
                    await(failFirst);
                    throw failure;
                }), callers);
                await(firstStarted);
                CompletableFuture<TerrainChunkSnapshot> second = CompletableFuture.supplyAsync(() -> {
                    secondThread.set(Thread.currentThread());
                    return cache.getOrBuildFor(0, 0, retryBuilder);
                }, callers);
                awaitWaiting(secondThread);

                failFirst.countDown();
                assertSame(failure, assertThrows(ExecutionException.class, () -> first.get(5, TimeUnit.SECONDS)).getCause());
                await(retryStarted);
                if (clearDuringRetry) {
                    cache.clear();
                }
                CompletableFuture<TerrainChunkSnapshot> third = CompletableFuture.supplyAsync(() -> {
                    thirdThread.set(Thread.currentThread());
                    return cache.getOrBuildFor(0, 0, retryBuilder);
                }, callers);
                awaitWaiting(thirdThread);
                assertEquals(1, retryBuilds.get(), "a later caller must wait for the existing retry, not acquire a new lock");

                finishRetry.countDown();
                assertSame(snapshot, second.get(5, TimeUnit.SECONDS));
                assertSame(snapshot, third.get(5, TimeUnit.SECONDS));
            } finally {
                failFirst.countDown();
                finishRetry.countDown();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "timed out waiting for test phase");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void awaitWaiting(AtomicReference<Thread> reference) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread thread = reference.get();
            if (thread != null && (thread.getState() == Thread.State.WAITING || thread.getState() == Thread.State.TIMED_WAITING)) {
                return;
            }
            Thread.sleep(1);
        }
        throw new AssertionError("caller did not block");
    }
}
