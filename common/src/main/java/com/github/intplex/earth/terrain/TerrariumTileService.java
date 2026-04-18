package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javax.imageio.ImageIO;

public final class TerrariumTileService extends AbstractRasterTileService<TerrariumTile> {
    static final int DEFAULT_MEMORY_CACHE_ENTRIES = TerrariumRuntimeConfig.DEFAULT_TERRAIN_TILE_CONFIG.cacheEntries();
    static final int PREFETCH_RADIUS = TerrariumRuntimeConfig.DEFAULT_TERRAIN_TILE_CONFIG.prefetchRadius();
    static final int DEFAULT_IO_THREADS = TerrariumRuntimeConfig.DEFAULT_IO_THREADS_PER_SERVICE;
    static final String DEFAULT_BASE_URL = "https://elevation-tiles-prod.s3.amazonaws.com/terrarium";

    private final int zoom;

    public TerrariumTileService(Config config) {
        super(
            config.executor(),
            new RemotePngTileStore<>(
                new RemotePngTileStore.StoreConfig<>(
                    config.diskCacheRoot(),
                    config.executor(),
                    config.downloader()::fetch,
                    TerrariumTileService::decodeTile,
                    key -> RemotePngTileStore.isValidEarthTile(key, config.zoom()),
                    config.memoryCacheEntries(),
                    config.prefetchRadius()
                )
            )
        );
        this.zoom = config.zoom();
    }

    static TerrariumTileService create(Path gameDir, int zoom) {
        return create(
            gameDir,
            zoom,
            DEFAULT_BASE_URL,
            TerrariumRuntimeConfig.DEFAULT_TERRAIN_TILE_CONFIG,
            DEFAULT_IO_THREADS
        );
    }

    static TerrariumTileService create(Path gameDir, int zoom, String baseUrl) {
        return create(
            gameDir,
            zoom,
            baseUrl,
            TerrariumRuntimeConfig.DEFAULT_TERRAIN_TILE_CONFIG,
            DEFAULT_IO_THREADS
        );
    }

    static TerrariumTileService create(
        Path gameDir,
        int zoom,
        String baseUrl,
        TerrariumRuntimeConfig.TileLayerConfig tileConfig,
        int ioThreads
    ) {
        return new TerrariumTileService(
            Config.runtime(
                gameDir,
                zoom,
                baseUrl,
                tileConfig.cacheEntries(),
                tileConfig.prefetchRadius(),
                ioThreads
            )
        );
    }

    static TerrariumTileService forTesting(Config config) {
        return new TerrariumTileService(config);
    }

    protected static ExecutorService createDefaultExecutor() {
        return createDefaultExecutor(DEFAULT_IO_THREADS);
    }

    protected static ExecutorService createDefaultExecutor(int ioThreads) {
        return AbstractRasterTileService.createDefaultExecutor(ioThreads);
    }

    int zoom() {
        return zoom;
    }

    private static TerrariumTile decodeTile(byte[] bytes, TileKey key) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new IllegalStateException("Terrarium tile " + key + " was not a valid PNG image");
            }
            return new TerrariumTile(key, image);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to decode tile " + key, exception);
        }
    }

    static URI buildTileUri(TileKey key, int zoom) {
        return buildTileUri(DEFAULT_BASE_URL, key, zoom);
    }

    static URI buildTileUri(String baseUrl, TileKey key, int zoom) {
        int validatedZoom = EarthGenConfig.validateZoom(zoom);
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        return URI.create(normalizedBaseUrl + "/" + validatedZoom + "/" + key.x() + "/" + key.y() + ".png");
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String trimmed = Objects.requireNonNull(baseUrl, "baseUrl").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    record Config(
        Path diskCacheRoot,
        ExecutorService executor,
        TileDownloader downloader,
        int memoryCacheEntries,
        int prefetchRadius,
        int zoom
    ) {
        Config {
            diskCacheRoot = Objects.requireNonNull(diskCacheRoot, "diskCacheRoot");
            executor = Objects.requireNonNull(executor, "executor");
            downloader = Objects.requireNonNull(downloader, "downloader");
            memoryCacheEntries = Math.max(1, memoryCacheEntries);
            prefetchRadius = Math.max(0, prefetchRadius);
            zoom = EarthGenConfig.validateZoom(zoom);
        }

        static Config runtime(Path gameDir, int zoom) {
            return runtime(gameDir, zoom, DEFAULT_BASE_URL, DEFAULT_MEMORY_CACHE_ENTRIES, PREFETCH_RADIUS, DEFAULT_IO_THREADS);
        }

        static Config runtime(Path gameDir, int zoom, String baseUrl) {
            return runtime(gameDir, zoom, baseUrl, DEFAULT_MEMORY_CACHE_ENTRIES, PREFETCH_RADIUS, DEFAULT_IO_THREADS);
        }

        static Config runtime(
            Path gameDir,
            int zoom,
            String baseUrl,
            int memoryCacheEntries,
            int prefetchRadius,
            int ioThreads
        ) {
            int validatedZoom = EarthGenConfig.validateZoom(zoom);
            return new Config(
                gameDir.resolve(Path.of("cache", "terrarium_expanded", "terrarium", Integer.toString(validatedZoom))),
                createDefaultExecutor(ioThreads),
                new HttpTileDownloader(baseUrl, validatedZoom),
                memoryCacheEntries,
                prefetchRadius,
                validatedZoom
            );
        }
    }

    interface TileDownloader {
        byte[] fetch(TileKey key) throws IOException;
    }

    static final class HttpTileDownloader implements TileDownloader {
        private final RemotePngTileStore.HttpTileFetcher fetcher;

        HttpTileDownloader(int zoom) {
            this(DEFAULT_BASE_URL, zoom);
        }

        HttpTileDownloader(String baseUrl, int zoom) {
            int validatedZoom = EarthGenConfig.validateZoom(zoom);
            String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
            this.fetcher = new RemotePngTileStore.HttpTileFetcher(
                key -> buildTileUri(normalizedBaseUrl, key, validatedZoom),
                RemotePngTileStore.HttpFetchConfig.defaults()
            );
        }

        @Override
        public byte[] fetch(TileKey key) throws IOException {
            return fetcher.fetch(key);
        }
    }
}
