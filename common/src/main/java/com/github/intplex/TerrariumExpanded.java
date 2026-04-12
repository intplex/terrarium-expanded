package com.github.intplex;

import com.github.intplex.earth.terrain.TerrainServices;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class TerrariumExpanded {
    public static final String MOD_ID = "terrarium_expanded";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void init(Path gameDir) {
        int javaFeature = Runtime.version().feature();
        if (javaFeature < 21) {
            throw new IllegalStateException(
                "Terrarium Expanded requires Java 21 or newer; current runtime is Java " + javaFeature
            );
        }
        TerrainServices.bootstrap(gameDir);
        LOGGER.info("Terrarium Expanded initialized with cache root at {}", gameDir.resolve("cache"));
    }
}
