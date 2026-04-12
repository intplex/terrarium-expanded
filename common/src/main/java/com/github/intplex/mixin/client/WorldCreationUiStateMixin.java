package com.github.intplex.mixin.client;

import com.github.intplex.TerrariumExpanded;
import com.github.intplex.client.world.EarthPresetEditorScreen;
import java.util.Optional;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {
    @Inject(method = "getPresetEditor", at = @At("RETURN"), cancellable = true)
    private void terrariumExpanded$injectEarthPresetEditor(CallbackInfoReturnable<PresetEditor> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }

        WorldCreationUiState self = (WorldCreationUiState) (Object) this;
        var worldTypeEntry = self.getWorldType();
        if (worldTypeEntry == null || worldTypeEntry.preset() == null) {
            return;
        }

        Optional<net.minecraft.resources.ResourceKey<WorldPreset>> worldPresetKey = worldTypeEntry.preset().unwrapKey();
        if (worldPresetKey.isEmpty() || !worldPresetKey.get().location().equals(TerrariumExpanded.id("earth"))) {
            return;
        }

        cir.setReturnValue(EarthPresetEditorScreen::create);
    }
}
