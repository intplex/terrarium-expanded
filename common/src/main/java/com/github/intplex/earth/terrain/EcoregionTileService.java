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

public final class EcoregionTileService extends AbstractRasterTileService<EcoregionTile> {
    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");
    static final int DEFAULT_MEMORY_CACHE_ENTRIES = 32;
    static final int PREFETCH_RADIUS = 0;
    static final String DEFAULT_BASE_URL = EarthGenerationProfile.DEFAULT_BIOMES_BASE_URL;

    public EcoregionTileService(Config config) {
        super(
            config.executor(),
            new RemotePngTileStore<>(
                new RemotePngTileStore.StoreConfig<>(
                    config.diskCacheRoot(),
                    config.executor(),
                    config.downloader()::fetch,
                    EcoregionTileService::decodeTile,
                    EcoregionTileService::isValidReducedTileKey,
                    config.memoryCacheEntries(),
                    config.prefetchRadius()
                )
            )
        );
    }

    static EcoregionTileService create(Path gameDir) {
        return new EcoregionTileService(Config.runtime(gameDir));
    }

    static EcoregionTileService create(Path gameDir, String baseUrl) {
        return new EcoregionTileService(Config.runtime(gameDir, baseUrl));
    }

    static EcoregionTileService forTesting(Config config) {
        return new EcoregionTileService(config);
    }

    protected static ExecutorService createDefaultExecutor() {
        return AbstractRasterTileService.createDefaultExecutor();
    }

    private static EcoregionTile decodeTile(byte[] bytes, TileKey key) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new IllegalStateException("Ecoregion tile " + key + " was not a valid PNG image");
            }
            return new EcoregionTile(key, image);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to decode ecoregion tile " + key, exception);
        }
    }

    static boolean isValidReducedTileKey(TileKey key) {
        int maxTile = EarthGenConfig.ECOREGION_REDUCED_TILE_COUNT_PER_AXIS;
        return key.x() >= 0 && key.x() < maxTile && key.y() >= 0 && key.y() < maxTile;
    }

    static URI buildTileUri(TileKey key) {
        return buildTileUri(DEFAULT_BASE_URL, key);
    }

    static URI buildTileUri(String baseUrl, TileKey key) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        return URI.create(normalizedBaseUrl + "/" + EarthGenConfig.ECOREGION_SOURCE_ZOOM + "/" + key.x() + "/" + key.y() + ".png");
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
        int prefetchRadius
    ) {
        Config {
            diskCacheRoot = Objects.requireNonNull(diskCacheRoot, "diskCacheRoot");
            executor = Objects.requireNonNull(executor, "executor");
            downloader = Objects.requireNonNull(downloader, "downloader");
            memoryCacheEntries = Math.max(1, memoryCacheEntries);
            prefetchRadius = Math.max(0, prefetchRadius);
        }

        static Config runtime(Path gameDir) {
            return runtime(gameDir, DEFAULT_BASE_URL);
        }

        static Config runtime(Path gameDir, String baseUrl) {
            return new Config(
                gameDir.resolve(Path.of("cache", "terrarium_expanded", "ecoregions", Integer.toString(EarthGenConfig.ECOREGION_SOURCE_ZOOM))),
                createDefaultExecutor(),
                new HttpTileDownloader(baseUrl),
                DEFAULT_MEMORY_CACHE_ENTRIES,
                PREFETCH_RADIUS
            );
        }
    }

    interface TileDownloader {
        byte[] fetch(TileKey key) throws IOException;
    }

    static final class HttpTileDownloader implements TileDownloader {
        private final RemotePngTileStore.HttpTileFetcher fetcher;

        HttpTileDownloader() {
            this(DEFAULT_BASE_URL);
        }

        HttpTileDownloader(String baseUrl) {
            String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
            LOGGER.info(
                "[TX-BIOME] reduced ecoregion tile URL set to {}/{} (tile_size={} request_grid={}x{})",
                normalizedBaseUrl,
                EarthGenConfig.ECOREGION_SOURCE_ZOOM,
                EarthGenConfig.ECOREGION_REDUCED_TILE_SIZE,
                EarthGenConfig.ECOREGION_REDUCED_TILE_COUNT_PER_AXIS,
                EarthGenConfig.ECOREGION_REDUCED_TILE_COUNT_PER_AXIS
            );
            this.fetcher = new RemotePngTileStore.HttpTileFetcher(
                key -> EcoregionTileService.buildTileUri(normalizedBaseUrl, key),
                RemotePngTileStore.HttpFetchConfig.defaults()
            );
        }

        @Override
        public byte[] fetch(TileKey key) throws IOException {
            return fetcher.fetch(key);
        }
    }
}
