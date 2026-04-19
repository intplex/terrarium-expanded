package com.github.intplex.mixin.client;

import com.github.intplex.earth.EarthGenConfig;
import com.github.intplex.earth.terrain.TerrariumSeamPatch;
import com.github.intplex.earth.terrain.TerrainServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @ModifyArg(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;renderLines(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;Z)V",
            ordinal = 1
        ),
        index = 1
    )
    private List<String> terrariumExpanded$appendLatLon(List<String> lines) {
        if (minecraft.level == null) {
            return lines;
        }

        Entity cameraEntity = minecraft.getCameraEntity();
        if (cameraEntity == null) {
            cameraEntity = minecraft.player;
        }
        if (cameraEntity == null) {
            return lines;
        }

        int blockX = cameraEntity.getBlockX();
        int blockZ = cameraEntity.getBlockZ();
        String line = EarthGenConfig.blockToGeo(blockX, blockZ)
            .map(geo -> String.format(Locale.ROOT, "Terrarium: lat %.5f, lon %.5f", geo.latitude(), geo.longitude()))
            .orElse("Terrarium: outside projected Earth bounds");
        String terrainLine = EarthGenConfig.projectBlockToTerrainTile(blockX, blockZ)
            .map(samplePoint -> {
                int zoom = EarthGenConfig.activeZoom();
                int patchedPixelX = TerrariumSeamPatch.patchedPixelX(zoom, samplePoint.tileKey(), samplePoint.pixelX());
                double meters = TerrainServices.tileService().requireTile(samplePoint.tileKey()).sampleMeters(patchedPixelX, samplePoint.pixelY());
                int terrainY = EarthGenConfig.mapMetersToTerrainY(meters);
                return String.format(Locale.ROOT, "Terrarium: %.2f m -> y %d (sea %d)", meters, terrainY, EarthGenConfig.SEA_LEVEL);
            })
            .orElse(String.format(Locale.ROOT, "Terrarium: terrain unavailable (sea %d)", EarthGenConfig.SEA_LEVEL));

        List<String> information = new ArrayList<>(lines);
        information.add(line);
        information.add(terrainLine);
        return information;
    }
}
