package com.github.intplex.earth.biome;

import com.github.intplex.earth.EarthGenConfig;
import com.github.intplex.earth.biome.EcoregionBiomeMappings.ResolvedBiomeMapping;
import com.github.intplex.earth.terrain.EarthSamplingFacade;
import com.github.intplex.earth.terrain.EarthSamplingResult;
import com.github.intplex.earth.terrain.EarthGenerationProfile;
import com.github.intplex.earth.terrain.EarthWorldgenToggles;
import com.github.intplex.earth.terrain.OceanSurfaceTemperatureService;
import com.github.intplex.earth.terrain.TerrainService;
import com.github.intplex.earth.terrain.TerrainServices;
import com.github.intplex.earth.terrain.TerrariumRuntimeConfig;
import com.github.intplex.earth.terrain.TileKey;
import com.github.intplex.earth.terrain.WaterBodyKind;
import java.util.LinkedHashMap;
import java.util.OptionalDouble;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EcoregionBiomeSource extends BiomeSource {
    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");
    public static final MapCodec<EcoregionBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
        .group(
            RegistryOps.retrieveGetter(Registries.BIOME),
            Codec.intRange(EarthGenConfig.MIN_ZOOM, EarthGenConfig.MAX_ZOOM)
                .optionalFieldOf("zoom", EarthGenConfig.DEFAULT_ZOOM)
                .forGetter(EcoregionBiomeSource::zoom),
            Codec.intRange(EarthGenConfig.MIN_MAX_MOUNTAIN_Y, EarthGenConfig.ABSOLUTE_MAX_TERRAIN_Y)
                .optionalFieldOf("max_mountain_y", EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y)
                .forGetter(EcoregionBiomeSource::maxMountainY),
            Codec.intRange(EarthGenConfig.MIN_TERRAIN_Y, EarthGenConfig.MAX_OCEAN_FLOOR_Y)
                .optionalFieldOf("ocean_floor_y", EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y)
                .forGetter(EcoregionBiomeSource::oceanFloorY),
            Codec.STRING
                .optionalFieldOf("terrain_base_url", EarthGenerationProfile.DEFAULT_TERRAIN_BASE_URL)
                .forGetter(EcoregionBiomeSource::terrainBaseUrl),
            Codec.STRING
                .optionalFieldOf("biomes_base_url", EarthGenerationProfile.DEFAULT_BIOMES_BASE_URL)
                .forGetter(EcoregionBiomeSource::biomesBaseUrl),
            Codec.STRING
                .optionalFieldOf("surface_water_base_url", EarthGenerationProfile.DEFAULT_SURFACE_WATER_BASE_URL)
                .forGetter(EcoregionBiomeSource::surfaceWaterBaseUrl),
            Codec.STRING
                .optionalFieldOf("terrain_fixes", EarthGenerationProfile.TERRAIN_FIXES_NONE)
                .forGetter(EcoregionBiomeSource::terrainFixes),
            Codec.BOOL
                .optionalFieldOf("world_border", false)
                .forGetter(EcoregionBiomeSource::worldBorder),
            Codec.doubleRange(-EarthGenConfig.MAX_MERCATOR_LATITUDE, EarthGenConfig.MAX_MERCATOR_LATITUDE)
                .optionalFieldOf("spawn_latitude", EarthGenerationProfile.DEFAULT_SPAWN_LATITUDE)
                .forGetter(EcoregionBiomeSource::spawnLatitude),
            Codec.doubleRange(EarthGenConfig.MIN_LONGITUDE, EarthGenConfig.MAX_LONGITUDE)
                .optionalFieldOf("spawn_longitude", EarthGenerationProfile.DEFAULT_SPAWN_LONGITUDE)
                .forGetter(EcoregionBiomeSource::spawnLongitude),
            BiomeIntegrationMode.CODEC
                .optionalFieldOf("biome_integration", BiomeIntegrationMode.AUTO)
                .forGetter(EcoregionBiomeSource::biomeIntegration),
            EarthWorldgenToggles.CODEC.codec()
                .optionalFieldOf("generation", EarthWorldgenToggles.defaults())
                .forGetter(EcoregionBiomeSource::worldgenToggles)
        )
        .apply(
            instance,
            (
                biomeLookup,
                zoom,
                maxMountainY,
                oceanFloorY,
                terrainBaseUrl,
                biomesBaseUrl,
                surfaceWaterBaseUrl,
                terrainFixes,
                worldBorder,
                spawnLatitude,
                spawnLongitude,
                biomeIntegration,
                generation
            ) ->
            new EcoregionBiomeSource(
                biomeLookup,
                new EarthGenerationProfile(
                    zoom,
                    maxMountainY,
                    oceanFloorY,
                    terrainBaseUrl,
                    biomesBaseUrl,
                    surfaceWaterBaseUrl,
                    terrainFixes,
                    generation,
                    worldBorder,
                    spawnLatitude,
                    spawnLongitude
                ),
                biomeIntegration
            )
        ));
    static final int DEEP_OCEAN_TERRAIN_Y_THRESHOLD = EarthGenConfig.mapMetersToTerrainY(-1000.0);
    static final double POLAR_SST_THRESHOLD_C = 2.0;
    static final double COLD_SST_THRESHOLD_C = 10.0;
    static final double TEMPERATE_SST_THRESHOLD_C = 18.0;
    static final double WARM_SST_THRESHOLD_C = 26.0;
    private static final int LOGGED_UNMAPPED_COLORS_CAPACITY = 8192;
    private static final BoundedLogSet<UnmappedColorKey> LOGGED_UNMAPPED_COLORS =
        new BoundedLogSet<>(LOGGED_UNMAPPED_COLORS_CAPACITY);
    private static final ColorSample OUT_OF_BOUNDS_COLOR_SAMPLE =
        new ColorSample(0, FallbackReason.OUT_OF_BOUNDS, null, -1, -1);
    private static final ThreadLocal<SamplingCacheState> SAMPLING_CACHES =
        ThreadLocal.withInitial(SamplingCacheState::new);

    private final ResolvedBiomeMapping mappings;
    private final ColorSampler colorSampler;
    private final TerrainYSampler terrainYSampler;
    private final InlandWaterSampler inlandWaterSampler;
    private final BiomeColdSampler biomeColdSampler;
    private final ThreadLocal<BiomeHotCache> biomeHotCache;
    private final EarthGenerationProfile profile;
    private final BiomeIntegrationMode biomeIntegration;

    public EcoregionBiomeSource(HolderGetter<Biome> biomeLookup, EarthGenerationProfile profile) {
        this(biomeLookup, profile, BiomeIntegrationMode.AUTO);
    }

    public EcoregionBiomeSource(HolderGetter<Biome> biomeLookup, EarthGenerationProfile profile, BiomeIntegrationMode biomeIntegration) {
        this(
            EcoregionBiomeMappings.resolveForBiomeLookup(biomeLookup, biomeIntegration),
            SamplingAdapters.defaults(),
            profile,
            biomeIntegration
        );
    }

    public EcoregionBiomeSource(
        ResolvedBiomeMapping mappings,
        SamplingAdapters samplingAdapters,
        EarthGenerationProfile profile
    ) {
        this(mappings, samplingAdapters, profile, BiomeIntegrationMode.AUTO);
    }

    public EcoregionBiomeSource(
        ResolvedBiomeMapping mappings,
        SamplingAdapters samplingAdapters,
        EarthGenerationProfile profile,
        BiomeIntegrationMode biomeIntegration
    ) {
        this.profile = new EarthGenerationProfile(
            profile.zoom(),
            profile.maxMountainY(),
            profile.oceanFloorY(),
            profile.terrainBaseUrl(),
            profile.biomesBaseUrl(),
            profile.surfaceWaterBaseUrl(),
            profile.terrainFixes(),
            profile.worldgenToggles(),
            profile.worldBorder(),
            profile.spawnLatitude(),
            profile.spawnLongitude()
        );
        this.biomeIntegration = biomeIntegration == null ? BiomeIntegrationMode.AUTO : biomeIntegration;
        this.mappings = mappings;
        this.colorSampler = samplingAdapters.colorSampler();
        this.terrainYSampler = samplingAdapters.terrainYSampler();
        this.inlandWaterSampler = samplingAdapters.inlandWaterSampler();
        this.biomeColdSampler = samplingAdapters.biomeColdSampler();
        this.biomeHotCache = ThreadLocal.withInitial(BiomeHotCache::new);
        TerrainServices.syncEarthProfile(this.profile);
    }

    public int zoom() {
        return profile.zoom();
    }

    public int maxMountainY() {
        return profile.maxMountainY();
    }

    public int oceanFloorY() {
        return profile.oceanFloorY();
    }

    public String terrainBaseUrl() {
        return profile.terrainBaseUrl();
    }

    public String surfaceWaterBaseUrl() {
        return profile.surfaceWaterBaseUrl();
    }

    public String biomesBaseUrl() {
        return profile.biomesBaseUrl();
    }

    public String terrainFixes() {
        return profile.terrainFixes();
    }

    public double spawnLatitude() {
        return profile.spawnLatitude();
    }

    public double spawnLongitude() {
        return profile.spawnLongitude();
    }

    public boolean worldBorder() {
        return profile.worldBorder();
    }

    public BiomeIntegrationMode biomeIntegration() {
        return biomeIntegration;
    }

    public EarthWorldgenToggles worldgenToggles() {
        return profile.worldgenToggles();
    }

    public static void resetFallbackCounters() {
        LOGGED_UNMAPPED_COLORS.clear();
        SAMPLING_CACHES.remove();
    }

    static boolean markUnmappedColorForTesting(TileKey tileKey, int colorRgb) {
        return LOGGED_UNMAPPED_COLORS.markIfNew(new UnmappedColorKey(tileKey, colorRgb));
    }

    static int loggedUnmappedColorCountForTesting() {
        return LOGGED_UNMAPPED_COLORS.size();
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return mappings.possibleBiomes().stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        BiomeHotCache hotCache = biomeHotCache.get();
        Holder<Biome> cachedBiome = hotCache.get(quartX, quartZ);
        if (cachedBiome != null) {
            return cachedBiome;
        }

        int blockX = QuartPos.toBlock(quartX);
        int blockZ = QuartPos.toBlock(quartZ);

        ColorSample sample = colorSampler.sampleColor(blockX, blockZ);
        Holder<Biome> baseBiome = mappings.byColor().get(sample.colorRgb());
        if (baseBiome == null) {
            int ecoregionColorForRecovery = sample.reason() == FallbackReason.UNMAPPED_COLOR ? sample.colorRgb() : -1;
            int terrainY = terrainYSampler.terrainYAtXZ(blockX, blockZ, ecoregionColorForRecovery);
            baseBiome = oceanFallbackForTerrainY(blockX, blockZ, terrainY);
            logOceanFallback(blockX, blockZ, sample, terrainY, deepOceanTerrainYThreshold(), baseBiome);
        }

        InlandWaterSample inlandWater = inlandWaterSampler.sampleInlandWater(blockX, blockZ);
        Holder<Biome> resolvedBiome = resolveInlandWaterBiomeOverride(
            blockX,
            blockZ,
            baseBiome,
            inlandWater,
            hotCache.snowCheckPosition()
        );
        hotCache.put(quartX, quartZ, resolvedBiome);
        return resolvedBiome;
    }

    private static ColorSample sampleEcoregionColor(int blockX, int blockZ) {
        EarthSamplingResult.EcoregionProbe probe = EarthSamplingFacade.sampleEcoregionColor(
            blockX,
            blockZ,
            samplingCaches()
        );
        return switch (probe.status()) {
            case OUT_OF_BOUNDS -> OUT_OF_BOUNDS_COLOR_SAMPLE;
            case TILE_LOAD_FAILURE -> new ColorSample(
                0,
                FallbackReason.TILE_LOAD_FAILURE,
                probe.tileKey(),
                probe.pixelX(),
                probe.pixelY()
            );
            case SAMPLED -> new ColorSample(
                probe.colorRgb(),
                FallbackReason.UNMAPPED_COLOR,
                probe.tileKey(),
                probe.pixelX(),
                probe.pixelY()
            );
        };
    }

    private static int sampleTerrainYFromTerrarium(int blockX, int blockZ, int ecoregionColorRgb) {
        EarthSamplingResult.TerrainProbe probe = EarthSamplingFacade.sampleTerrainWithEcoregionColorHint(
            blockX,
            blockZ,
            ecoregionColorRgb,
            samplingCaches()
        );
        if (!probe.inBounds()) {
            return EarthGenConfig.MIN_TERRAIN_Y;
        }
        return probe.terrainY();
    }

    private static InlandWaterSample sampleInlandWater(int blockX, int blockZ) {
        return new InlandWaterSample(
            TerrainService.inlandWaterKindAtXZ(blockX, blockZ),
            TerrainService.inlandWaterSurfaceYAtXZ(blockX, blockZ)
        );
    }

    private Holder<Biome> resolveInlandWaterBiomeOverride(
        int blockX,
        int blockZ,
        Holder<Biome> baseBiome,
        InlandWaterSample inlandWaterSample,
        BlockPos.MutableBlockPos snowCheckPos
    ) {
        if (inlandWaterSample.kind() == WaterBodyKind.NONE) {
            return baseBiome;
        }
        boolean frozen = biomeColdSampler.isFrozen(baseBiome, blockX, inlandWaterSample.waterSurfaceY(), blockZ, snowCheckPos);
        return frozen ? mappings.frozenRiverBiome() : mappings.riverBiome();
    }

    private static boolean isColdEnoughToSnowAt(
        Holder<Biome> biome,
        int blockX,
        int blockY,
        int blockZ,
        BlockPos.MutableBlockPos reusablePos
    ) {
        reusablePos.set(blockX, blockY, blockZ);
        return biome.value().coldEnoughToSnow(reusablePos);
    }

    Holder<Biome> oceanFallbackForTerrainY(int blockX, int blockZ, int terrainY) {
        OptionalDouble sampledSst;
        try {
            sampledSst = OceanSurfaceTemperatureService.sampleMeanAnnualSstAtBlock(blockX, blockZ, profile.zoom());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                "[TX-BIOME] ocean SST sampling failed block=({}, {}) zoom={} error={}; using legacy ocean fallback",
                blockX,
                blockZ,
                profile.zoom(),
                exception.toString()
            );
            return terrainY <= deepOceanTerrainYThreshold() ? mappings.deepOceanBiome() : mappings.oceanBiome();
        }

        if (sampledSst.isEmpty() || !Double.isFinite(sampledSst.getAsDouble())) {
            return terrainY <= deepOceanTerrainYThreshold() ? mappings.deepOceanBiome() : mappings.oceanBiome();
        }
        return oceanFallbackForTerrainAndSst(terrainY, sampledSst.getAsDouble());
    }

    Holder<Biome> oceanFallbackForTerrainAndSst(int terrainY, double sstCelsius) {
        if (terrainY <= continentalShelfTerrainYThreshold()) {
            // Minecraft has one deep-ocean tier for both upper slope and abyssal depths.
            return deepOceanBiomeForSst(sstCelsius);
        }
        return shallowOceanBiomeForSst(sstCelsius);
    }

    int continentalShelfTerrainYThreshold() {
        return EarthGenConfig.mapMetersToTerrainY(-200.0);
    }

    private Holder<Biome> shallowOceanBiomeForSst(double sstCelsius) {
        if (sstCelsius < POLAR_SST_THRESHOLD_C) {
            return mappings.frozenOceanBiome();
        }
        if (sstCelsius < COLD_SST_THRESHOLD_C) {
            return mappings.coldOceanBiome();
        }
        if (sstCelsius < TEMPERATE_SST_THRESHOLD_C) {
            return mappings.oceanBiome();
        }
        if (sstCelsius < WARM_SST_THRESHOLD_C) {
            return mappings.lukewarmOceanBiome();
        }
        return mappings.warmOceanBiome();
    }

    private Holder<Biome> deepOceanBiomeForSst(double sstCelsius) {
        if (sstCelsius < POLAR_SST_THRESHOLD_C) {
            return mappings.deepFrozenOceanBiome();
        }
        if (sstCelsius < COLD_SST_THRESHOLD_C) {
            return mappings.deepColdOceanBiome();
        }
        if (sstCelsius < TEMPERATE_SST_THRESHOLD_C) {
            return mappings.deepOceanBiome();
        }
        return mappings.deepLukewarmOceanBiome();
    }

    int deepOceanTerrainYThreshold() {
        return EarthGenConfig.mapMetersToTerrainY(-1000.0);
    }

    private static void logOceanFallback(
        int blockX,
        int blockZ,
        ColorSample sample,
        int terrainY,
        int deepOceanThreshold,
        Holder<Biome> fallbackBiome
    ) {
        if (sample.reason() != FallbackReason.UNMAPPED_COLOR || sample.tileKey() == null) {
            return;
        }

        UnmappedColorKey unmappedKey = new UnmappedColorKey(sample.tileKey(), sample.colorRgb());
        if (!LOGGED_UNMAPPED_COLORS.markIfNew(unmappedKey)) {
            return;
        }

        String pixelText = sample.pixelX() >= 0 && sample.pixelY() >= 0 ? sample.pixelX() + "," + sample.pixelY() : "<none>";
        String fallbackBiomeId = fallbackBiome.unwrapKey().map(key -> key.location().toString()).orElse("<unbound>");
        LOGGER.warn(
            "[TX-BIOME] no biome mapping for ecoregion color=#{} block=({}, {}) tile={} pixel={} terrainY={} deep_threshold={} fallback={}",
            String.format("%06X", sample.colorRgb()),
            blockX,
            blockZ,
            sample.tileKey(),
            pixelText,
            terrainY,
            deepOceanThreshold,
            fallbackBiomeId
        );
    }

    @FunctionalInterface
    interface ColorSampler {
        ColorSample sampleColor(int blockX, int blockZ);
    }

    @FunctionalInterface
    interface TerrainYSampler {
        int terrainYAtXZ(int blockX, int blockZ, int ecoregionColorRgb);
    }

    @FunctionalInterface
    interface InlandWaterSampler {
        InlandWaterSample sampleInlandWater(int blockX, int blockZ);
    }

    @FunctionalInterface
    interface BiomeColdSampler {
        boolean isFrozen(Holder<Biome> biome, int blockX, int blockY, int blockZ, BlockPos.MutableBlockPos reusablePos);
    }

    enum FallbackReason {
        UNMAPPED_COLOR,
        OUT_OF_BOUNDS,
        TILE_LOAD_FAILURE
    }

    record ColorSample(
        int colorRgb,
        FallbackReason reason,
        TileKey tileKey,
        int pixelX,
        int pixelY
    ) {
    }

    record InlandWaterSample(WaterBodyKind kind, int waterSurfaceY) {
    }

    public record SamplingAdapters(
        ColorSampler colorSampler,
        TerrainYSampler terrainYSampler,
        InlandWaterSampler inlandWaterSampler,
        BiomeColdSampler biomeColdSampler
    ) {
        private static final SamplingAdapters DEFAULT =
            new SamplingAdapters(
                EcoregionBiomeSource::sampleEcoregionColor,
                EcoregionBiomeSource::sampleTerrainYFromTerrarium,
                EcoregionBiomeSource::sampleInlandWater,
                EcoregionBiomeSource::isColdEnoughToSnowAt
            );

        public SamplingAdapters {
            colorSampler = java.util.Objects.requireNonNull(colorSampler, "colorSampler");
            terrainYSampler = java.util.Objects.requireNonNull(terrainYSampler, "terrainYSampler");
            inlandWaterSampler = java.util.Objects.requireNonNull(inlandWaterSampler, "inlandWaterSampler");
            biomeColdSampler = java.util.Objects.requireNonNull(biomeColdSampler, "biomeColdSampler");
        }

        public static SamplingAdapters defaults() {
            return DEFAULT;
        }
    }

    private record UnmappedColorKey(TileKey tileKey, int colorRgb) {
    }

    private static EarthSamplingFacade.LocalTileCaches samplingCaches() {
        SamplingCacheState state = SAMPLING_CACHES.get();
        long currentGeneration = TerrainServices.runtimeGeneration();
        int configuredEntries = TerrainServices.biomeSamplingCacheEntries();
        int idleSeconds = TerrainServices.samplingThreadLocalIdleSeconds();
        long nowNanos = System.nanoTime();
        if (state.runtimeGeneration() != currentGeneration) {
            state.recreateCaches(configuredEntries);
            state.setRuntimeGeneration(currentGeneration);
        } else if (state.maxEntriesPerLayer() != configuredEntries) {
            state.recreateCaches(configuredEntries);
        } else if (isIdleExpired(state.lastAccessNanos(), idleSeconds, nowNanos)) {
            state.caches().clear();
        }
        state.setLastAccessNanos(nowNanos);
        return state.caches();
    }

    private static final class BiomeHotCache {
        private int latestQuartX = Integer.MIN_VALUE;
        private int latestQuartZ = Integer.MIN_VALUE;
        private Holder<Biome> latestBiome;
        private int previousQuartX = Integer.MIN_VALUE;
        private int previousQuartZ = Integer.MIN_VALUE;
        private Holder<Biome> previousBiome;
        private final BlockPos.MutableBlockPos snowCheckPosition = new BlockPos.MutableBlockPos();

        private Holder<Biome> get(int quartX, int quartZ) {
            if (quartX == latestQuartX && quartZ == latestQuartZ) {
                return latestBiome;
            }
            if (quartX == previousQuartX && quartZ == previousQuartZ) {
                int promotedQuartX = previousQuartX;
                int promotedQuartZ = previousQuartZ;
                Holder<Biome> promotedBiome = previousBiome;
                previousQuartX = latestQuartX;
                previousQuartZ = latestQuartZ;
                previousBiome = latestBiome;
                latestQuartX = promotedQuartX;
                latestQuartZ = promotedQuartZ;
                latestBiome = promotedBiome;
                return promotedBiome;
            }
            return null;
        }

        private void put(int quartX, int quartZ, Holder<Biome> biome) {
            if (biome == null) {
                return;
            }
            if (quartX == latestQuartX && quartZ == latestQuartZ) {
                latestBiome = biome;
                return;
            }
            previousQuartX = latestQuartX;
            previousQuartZ = latestQuartZ;
            previousBiome = latestBiome;
            latestQuartX = quartX;
            latestQuartZ = quartZ;
            latestBiome = biome;
        }

        private BlockPos.MutableBlockPos snowCheckPosition() {
            return snowCheckPosition;
        }
    }

    private static final class SamplingCacheState {
        private EarthSamplingFacade.LocalTileCaches caches;
        private int maxEntriesPerLayer;
        private long runtimeGeneration = Long.MIN_VALUE;
        private long lastAccessNanos = Long.MIN_VALUE;

        private SamplingCacheState() {
            this.maxEntriesPerLayer = TerrariumRuntimeConfig.DEFAULT_SAMPLING_CONFIG.biomeLocalCacheEntries();
            this.caches = EarthSamplingFacade.LocalTileCaches.hotPathCaches(this.maxEntriesPerLayer);
        }

        private EarthSamplingFacade.LocalTileCaches caches() {
            return caches;
        }

        private int maxEntriesPerLayer() {
            return maxEntriesPerLayer;
        }

        private void recreateCaches(int maxEntriesPerLayer) {
            this.maxEntriesPerLayer = Math.max(1, maxEntriesPerLayer);
            this.caches = EarthSamplingFacade.LocalTileCaches.hotPathCaches(this.maxEntriesPerLayer);
        }

        private long runtimeGeneration() {
            return runtimeGeneration;
        }

        private void setRuntimeGeneration(long runtimeGeneration) {
            this.runtimeGeneration = runtimeGeneration;
        }

        private long lastAccessNanos() {
            return lastAccessNanos;
        }

        private void setLastAccessNanos(long lastAccessNanos) {
            this.lastAccessNanos = lastAccessNanos;
        }
    }

    private static boolean isIdleExpired(long lastAccessNanos, int idleSeconds, long nowNanos) {
        if (idleSeconds <= 0 || lastAccessNanos == Long.MIN_VALUE) {
            return false;
        }
        long idleNanos = java.util.concurrent.TimeUnit.SECONDS.toNanos(idleSeconds);
        return nowNanos - lastAccessNanos >= idleNanos;
    }

    private static final class BoundedLogSet<T> {
        private final int maxEntries;
        private final LinkedHashMap<T, Boolean> entries;

        private BoundedLogSet(int maxEntries) {
            this.maxEntries = Math.max(1, maxEntries);
            this.entries = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<T, Boolean> eldest) {
                    return size() > BoundedLogSet.this.maxEntries;
                }
            };
        }

        private synchronized boolean markIfNew(T key) {
            if (entries.containsKey(key)) {
                return false;
            }
            entries.put(key, Boolean.TRUE);
            return true;
        }

        private synchronized void clear() {
            entries.clear();
        }

        private synchronized int size() {
            return entries.size();
        }
    }
}
