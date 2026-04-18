package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RemotePngTileStore<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");
    private static final String MISSING_TILE_SUFFIX = ".missing";
    static final int DEFAULT_MEMORY_CACHE_ENTRIES = 512;
    static final int DEFAULT_PREFETCH_RADIUS = 1;
    static final int DEFAULT_IO_THREADS = 2;
    static final int DEFAULT_MAX_FETCH_ATTEMPTS = 3;
    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(4);
    static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final Path diskCacheRoot;
    private final ExecutorService executor;
    private final TileFetcher downloader;
    private final TileDecoder<T> decoder;
    private final TileValidator tileValidator;
    private final int prefetchRadius;

    private final MemoryLruCache<T> memoryCache;
    private final ConcurrentMap<TileKey, CompletableFuture<T>> inFlight;

    RemotePngTileStore(StoreConfig<T> config) {
        Objects.requireNonNull(config, "config");
        this.diskCacheRoot = config.diskCacheRoot();
        this.executor = config.executor();
        this.downloader = config.downloader();
        this.decoder = config.decoder();
        this.tileValidator = config.tileValidator();
        this.prefetchRadius = config.prefetchRadius();
        this.memoryCache = new MemoryLruCache<>(config.memoryCacheEntries(), config.memoryCacheTtlSeconds());
        this.inFlight = new ConcurrentHashMap<>();
    }

    T requireTile(TileKey key) {
        T cached = memoryCache.get(key);
        if (cached != null) {
            return cached;
        }

        T loaded = getOrLoad(key);
        prefetchNeighbors(key);
        return loaded;
    }

    T getOrLoad(TileKey key) {
        T cached = memoryCache.get(key);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<T> future = inFlight.computeIfAbsent(key, this::startLoad);
        try {
            return future.join();
        } catch (CompletionException completionException) {
            Throwable cause = completionException.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to load tile " + key, cause);
        }
    }

    private CompletableFuture<T> startLoad(TileKey key) {
        return CompletableFuture
            .supplyAsync(() -> loadTileBlocking(key), executor)
            .whenComplete((unused, throwable) -> inFlight.remove(key));
    }

    private T loadTileBlocking(TileKey key) {
        T memoryHit = memoryCache.get(key);
        if (memoryHit != null) {
            return memoryHit;
        }

        Path tilePath = tilePath(key);
        Path missingTilePath = missingTilePath(key);
        if (Files.exists(missingTilePath)) {
            throw new MissingTileException(key, "Tile was previously marked missing");
        }
        T loaded;
        if (Files.exists(tilePath)) {
            loaded = tryLoadFromDisk(tilePath, key);
            if (loaded == null) {
                deleteQuietly(tilePath);
                loaded = downloadAndPersistTile(key, tilePath);
            }
        } else {
            loaded = downloadAndPersistTile(key, tilePath);
        }

        memoryCache.put(key, loaded);
        return loaded;
    }

    private T tryLoadFromDisk(Path tilePath, TileKey key) {
        try {
            byte[] bytes = Files.readAllBytes(tilePath);
            return decoder.decode(bytes, key);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private T downloadAndPersistTile(TileKey key, Path tilePath) {
        try {
            byte[] bytes = downloader.fetch(key);
            T tile = decoder.decode(bytes, key);
            persistTile(tilePath, bytes);
            deleteQuietly(missingTilePath(key));
            return tile;
        } catch (HttpStatusException exception) {
            if (exception.statusCode() == 404 || exception.statusCode() == 410) {
                persistMissingTileMarker(key, exception);
                throw new MissingTileException(key, "HTTP " + exception.statusCode() + " while fetching tile", exception);
            }
            throw new IllegalStateException("Unable to fetch tile " + key, exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fetch tile " + key, exception);
        }
    }

    private void persistTile(Path tilePath, byte[] bytes) throws IOException {
        Files.createDirectories(tilePath.getParent());
        Path tempFile = tilePath.resolveSibling(tilePath.getFileName() + ".part");
        Files.write(tempFile, bytes);
        try {
            Files.move(tempFile, tilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tempFile, tilePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void prefetchNeighbors(TileKey center) {
        if (prefetchRadius <= 0) {
            return;
        }
        for (int dx = -prefetchRadius; dx <= prefetchRadius; dx++) {
            for (int dy = -prefetchRadius; dy <= prefetchRadius; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                TileKey neighbor = new TileKey(center.x() + dx, center.y() + dy);
                if (!tileValidator.isValid(neighbor)) {
                    continue;
                }
                enqueuePrefetch(neighbor);
            }
        }
    }

    private void enqueuePrefetch(TileKey key) {
        if (memoryCache.get(key) != null) {
            return;
        }
        inFlight.computeIfAbsent(key, this::startLoad);
    }

    private Path tilePath(TileKey key) {
        return diskCacheRoot.resolve(Integer.toString(key.x())).resolve(key.y() + ".png");
    }

    private Path missingTilePath(TileKey key) {
        return diskCacheRoot.resolve(Integer.toString(key.x())).resolve(key.y() + ".png" + MISSING_TILE_SUFFIX);
    }

    private void persistMissingTileMarker(TileKey key, HttpStatusException exception) {
        Path markerPath = missingTilePath(key);
        try {
            Files.createDirectories(markerPath.getParent());
            String markerBody = "status=" + exception.statusCode() + System.lineSeparator() + "uri=" + exception.uri() + System.lineSeparator();
            Files.writeString(markerPath, markerBody);
        } catch (IOException markerWriteException) {
            LOGGER.debug("Unable to persist missing-tile marker for {}: {}", key, markerWriteException.toString());
        }
    }

    static boolean isValidEarthTile(TileKey key) {
        return isValidEarthTile(key, EarthGenConfig.activeZoom());
    }

    static boolean isValidEarthTile(TileKey key, int zoom) {
        int maxTile = EarthGenConfig.tileCountPerAxis(zoom);
        return key.x() >= 0 && key.x() < maxTile && key.y() >= 0 && key.y() < maxTile;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    int memoryCacheEntries() {
        return memoryCache.maxEntries();
    }

    int memoryCacheTtlSeconds() {
        return memoryCache.ttlSeconds();
    }

    int prefetchRadius() {
        return prefetchRadius;
    }

    static ExecutorService createDefaultExecutor(int ioThreads) {
        int threads = Math.max(1, ioThreads);
        NamingCounter counter = new NamingCounter();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "remote-png-tile-io-" + counter.next());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(threads, factory);
    }

    interface TileDecoder<T> {
        T decode(byte[] bytes, TileKey key);
    }

    interface TileFetcher {
        byte[] fetch(TileKey key) throws IOException;
    }

    interface TileUrlBuilder {
        URI build(TileKey key);
    }

    interface TileValidator {
        boolean isValid(TileKey key);
    }

    record StoreConfig<T>(
        Path diskCacheRoot,
        ExecutorService executor,
        TileFetcher downloader,
        TileDecoder<T> decoder,
        TileValidator tileValidator,
        int memoryCacheEntries,
        int memoryCacheTtlSeconds,
        int prefetchRadius
    ) {
        StoreConfig {
            diskCacheRoot = Objects.requireNonNull(diskCacheRoot, "diskCacheRoot");
            executor = Objects.requireNonNull(executor, "executor");
            downloader = Objects.requireNonNull(downloader, "downloader");
            decoder = Objects.requireNonNull(decoder, "decoder");
            tileValidator = Objects.requireNonNull(tileValidator, "tileValidator");
            memoryCacheEntries = Math.max(1, memoryCacheEntries);
            memoryCacheTtlSeconds = Math.max(0, memoryCacheTtlSeconds);
            prefetchRadius = Math.max(0, prefetchRadius);
        }
    }

    record HttpFetchConfig(int maxFetchAttempts, Duration connectTimeout, Duration requestTimeout) {
        private static final HttpFetchConfig DEFAULT =
            new HttpFetchConfig(DEFAULT_MAX_FETCH_ATTEMPTS, DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);

        HttpFetchConfig {
            maxFetchAttempts = Math.max(1, maxFetchAttempts);
            connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout");
            requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        }

        static HttpFetchConfig defaults() {
            return DEFAULT;
        }
    }

    static final class HttpTileFetcher implements TileFetcher {
        private final HttpClient client;
        private final TileUrlBuilder urlBuilder;
        private final int maxFetchAttempts;
        private final Duration requestTimeout;

        HttpTileFetcher(TileUrlBuilder urlBuilder, HttpFetchConfig config) {
            this.urlBuilder = Objects.requireNonNull(urlBuilder);
            Objects.requireNonNull(config, "config");
            this.maxFetchAttempts = config.maxFetchAttempts();
            this.requestTimeout = config.requestTimeout();
            this.client = HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build();
        }

        @Override
        public byte[] fetch(TileKey key) throws IOException {
            URI uri = urlBuilder.build(key);
            IOException lastException = null;
            for (int attempt = 1; attempt <= maxFetchAttempts; attempt++) {
                HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(requestTimeout)
                    .build();
                try {
                    HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    if (response.statusCode() == 200) {
                        return response.body();
                    }
                    if (isNonRetriableStatus(response.statusCode())) {
                        throw new HttpStatusException(response.statusCode(), uri);
                    }
                    if (attempt == maxFetchAttempts) {
                        LOGGER.warn(
                            "[TX-WORLDGEN] tile fetch failed with status {} key={} attempt={} uri={}",
                            response.statusCode(),
                            key,
                            attempt,
                            uri
                        );
                    }
                    lastException = new IOException("Unexpected HTTP status " + response.statusCode() + " for " + uri);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while requesting " + uri, exception);
                } catch (HttpStatusException exception) {
                    LOGGER.debug(
                        "[TX-WORLDGEN] tile fetch returned non-retriable status {} key={} uri={}",
                        exception.statusCode(),
                        key,
                        uri
                    );
                    throw exception;
                } catch (IOException exception) {
                    if (attempt == maxFetchAttempts) {
                        LOGGER.warn(
                            "[TX-WORLDGEN] tile fetch IOException key={} attempt={} uri={} error={}",
                            key,
                            attempt,
                            uri,
                            exception.toString()
                        );
                    }
                    lastException = exception;
                }
            }
            throw new IOException("Failed to fetch tile " + key + " after " + maxFetchAttempts + " attempts", lastException);
        }

        private static boolean isNonRetriableStatus(int statusCode) {
            return statusCode >= 400 && statusCode < 500 && statusCode != 408 && statusCode != 429;
        }
    }

    static final class HttpStatusException extends IOException {
        private final int statusCode;
        private final URI uri;

        HttpStatusException(int statusCode, URI uri) {
            super("Unexpected HTTP status " + statusCode + " for " + uri);
            this.statusCode = statusCode;
            this.uri = uri;
        }

        int statusCode() {
            return statusCode;
        }

        URI uri() {
            return uri;
        }
    }

    static final class MissingTileException extends IllegalStateException {
        MissingTileException(TileKey key, String detail) {
            super("Known missing tile " + key + ": " + detail);
        }

        MissingTileException(TileKey key, String detail, Throwable cause) {
            super("Known missing tile " + key + ": " + detail, cause);
        }
    }

    private static final class MemoryLruCache<T> {
        private final int maxEntries;
        private final long ttlNanos;
        private final Map<TileKey, CacheEntry<T>> delegate;

        MemoryLruCache(int maxEntries, int ttlSeconds) {
            this.maxEntries = maxEntries;
            this.ttlNanos = ttlSeconds <= 0 ? 0L : Duration.ofSeconds(ttlSeconds).toNanos();
            this.delegate = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<TileKey, CacheEntry<T>> eldest) {
                    return size() > MemoryLruCache.this.maxEntries;
                }
            };
        }

        synchronized T get(TileKey key) {
            long now = System.nanoTime();
            evictExpiredEntries(now);
            CacheEntry<T> entry = delegate.get(key);
            if (entry == null) {
                return null;
            }
            if (isExpired(entry, now)) {
                delegate.remove(key);
                return null;
            }
            entry.lastAccessNanos = now;
            return entry.value;
        }

        synchronized void put(TileKey key, T value) {
            long now = System.nanoTime();
            evictExpiredEntries(now);
            delegate.put(
                Objects.requireNonNull(key),
                new CacheEntry<>(Objects.requireNonNull(value), now)
            );
        }

        int maxEntries() {
            return maxEntries;
        }

        int ttlSeconds() {
            if (ttlNanos <= 0L) {
                return 0;
            }
            return (int) TimeUnit.NANOSECONDS.toSeconds(ttlNanos);
        }

        private boolean isExpired(CacheEntry<T> entry, long nowNanos) {
            return ttlNanos > 0L && nowNanos - entry.lastAccessNanos >= ttlNanos;
        }

        private void evictExpiredEntries(long nowNanos) {
            if (ttlNanos <= 0L || delegate.isEmpty()) {
                return;
            }
            java.util.Iterator<Map.Entry<TileKey, CacheEntry<T>>> iterator = delegate.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<TileKey, CacheEntry<T>> entry = iterator.next();
                if (isExpired(entry.getValue(), nowNanos)) {
                    iterator.remove();
                }
            }
        }
    }

    private static final class CacheEntry<T> {
        private final T value;
        private long lastAccessNanos;

        private CacheEntry(T value, long lastAccessNanos) {
            this.value = value;
            this.lastAccessNanos = lastAccessNanos;
        }
    }

    private static final class NamingCounter {
        private int value;

        synchronized int next() {
            value++;
            return value;
        }
    }
}
