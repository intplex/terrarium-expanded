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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SurfaceWaterTileService extends AbstractRasterTileService<SurfaceWaterTile> {
    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");
    static final int DEFAULT_MEMORY_CACHE_ENTRIES = RemotePngTileStore.DEFAULT_MEMORY_CACHE_ENTRIES;
    static final int PREFETCH_RADIUS = RemotePngTileStore.DEFAULT_PREFETCH_RADIUS;
    static final String DEFAULT_BASE_URL = "https://storage.googleapis.com/global-surface-water/tiles2021/seasonality";

    private final int zoom;

    public SurfaceWaterTileService(Config config) {
        super(
            config.executor(),
            new RemotePngTileStore<>(
                new RemotePngTileStore.StoreConfig<>(
                    config.diskCacheRoot(),
                    config.executor(),
                    config.downloader()::fetch,
                    SurfaceWaterTileService::decodeTile,
                    key -> RemotePngTileStore.isValidEarthTile(key, config.zoom()),
                    config.memoryCacheEntries(),
                    config.prefetchRadius()
                )
            )
        );
        this.zoom = config.zoom();
    }

    static SurfaceWaterTileService create(Path gameDir, int zoom) {
        return new SurfaceWaterTileService(Config.runtime(gameDir, zoom));
    }

    static SurfaceWaterTileService create(Path gameDir, int zoom, String baseUrl) {
        return new SurfaceWaterTileService(Config.runtime(gameDir, zoom, baseUrl));
    }

    static SurfaceWaterTileService forTesting(Config config) {
        return new SurfaceWaterTileService(config);
    }

    protected static ExecutorService createDefaultExecutor() {
        return AbstractRasterTileService.createDefaultExecutor();
    }

    int zoom() {
        return zoom;
    }

    private static SurfaceWaterTile decodeTile(byte[] bytes, TileKey key) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new IllegalStateException("Surface water tile " + key + " was not a valid PNG image");
            }
            return new SurfaceWaterTile(key, image);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to decode surface water tile " + key, exception);
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
            return runtime(gameDir, zoom, DEFAULT_BASE_URL);
        }

        static Config runtime(Path gameDir, int zoom, String baseUrl) {
            int validatedZoom = EarthGenConfig.validateZoom(zoom);
            return new Config(
                gameDir.resolve(Path.of("cache", "terrarium_expanded", "surface_water", Integer.toString(validatedZoom))),
                createDefaultExecutor(),
                new HttpTileDownloader(baseUrl, validatedZoom),
                DEFAULT_MEMORY_CACHE_ENTRIES,
                PREFETCH_RADIUS,
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
            LOGGER.info("[TX-WATER] surface water tile base URL set to {}/{}", normalizedBaseUrl, validatedZoom);
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
