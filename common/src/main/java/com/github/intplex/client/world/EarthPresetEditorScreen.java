package com.github.intplex.client.world;

import com.github.intplex.earth.EarthGenConfig;
import com.github.intplex.earth.biome.BiomeIntegrationMode;
import com.github.intplex.earth.biome.EcoregionBiomeSource;
import com.github.intplex.earth.terrain.EarthGenerationProfile;
import com.github.intplex.earth.terrain.EarthWorldgenToggles;
import dev.architectury.platform.Platform;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

public final class EarthPresetEditorScreen extends Screen {
    private static final List<Integer> SUPPORTED_ZOOMS = supportedZooms();
    private static final Component TITLE = Component.translatable("terrarium_expanded.customize.earth.title");
    private static final Component ZOOM_LABEL = Component.translatable("terrarium_expanded.customize.earth.zoom");
    private static final Component MAX_MOUNTAIN_LABEL = Component.translatable("terrarium_expanded.customize.earth.max_mountain_y");
    private static final Component OCEAN_DEPTH_LABEL = Component.translatable("terrarium_expanded.customize.earth.ocean_floor_y");
    private static final Component SPAWN_LATITUDE_LABEL =
        Component.translatable("terrarium_expanded.customize.earth.spawn_latitude");
    private static final Component SPAWN_LONGITUDE_LABEL =
        Component.translatable("terrarium_expanded.customize.earth.spawn_longitude");
    private static final Component TERRAIN_BASE_URL_LABEL =
        Component.translatable("terrarium_expanded.customize.earth.terrain_base_url");
    private static final Component BIOMES_BASE_URL_LABEL =
        Component.translatable("terrarium_expanded.customize.earth.biomes_base_url");
    private static final Component SURFACE_WATER_BASE_URL_LABEL =
        Component.translatable("terrarium_expanded.customize.earth.surface_water_base_url");
    private static final Component TERRAIN_FIXES_LABEL =
        Component.translatable("terrarium_expanded.customize.earth.terrain_fixes");
    private static final Component BIOME_INTEGRATION_LABEL =
        Component.translatable("terrarium_expanded.customize.earth.biome_integration");
    private static final Component BIOMES_O_PLENTY_MISSING_WARNING =
        Component.translatable("terrarium_expanded.customize.earth.biome_integration.bop_missing");
    private static final Component TOGGLES_HEADER =
        Component.translatable("terrarium_expanded.customize.earth.generation_settings");
    private static final Component CAVES_LABEL = Component.translatable("terrarium_expanded.customize.earth.caves");
    private static final Component CANYONS_LABEL = Component.translatable("terrarium_expanded.customize.earth.canyons");
    private static final Component EXTRA_UNDERGROUND_LABEL =
        Component.translatable("terrarium_expanded.customize.earth.extra_underground");
    private static final Component AQUIFERS_LABEL = Component.translatable("terrarium_expanded.customize.earth.aquifers");
    private static final Component LAVA_AQUIFERS_LABEL =
        Component.translatable("terrarium_expanded.customize.earth.lava_aquifers");
    private static final Component VILLAGES_LABEL = Component.translatable("terrarium_expanded.customize.earth.villages");
    private static final Component WORLD_BORDER_LABEL =
        Component.translatable("terrarium_expanded.customize.earth.world_border");
    private static final Component INVALID_URL_INPUT =
        Component.translatable("terrarium_expanded.customize.earth.invalid_url_input");
    private static final Component INVALID_GEO_INPUT =
        Component.translatable(
            "terrarium_expanded.customize.earth.invalid_geo_input",
            -EarthGenConfig.MAX_MERCATOR_LATITUDE,
            EarthGenConfig.MAX_MERCATOR_LATITUDE,
            EarthGenConfig.MIN_LONGITUDE,
            EarthGenConfig.MAX_LONGITUDE
        );

    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    private static final int SECTION_GAP = 12;
    private static final int LABEL_GAP = 10;

    private final CreateWorldScreen parent;
    private final boolean biomesOPlentyLoaded;
    private final int maxTerrainYLimit;

    private int selectedZoom;
    private int selectedMaxMountainY;
    private int selectedOceanFloorY;
    private double selectedSpawnLatitude;
    private double selectedSpawnLongitude;
    private String selectedTerrainBaseUrl;
    private String selectedBiomesBaseUrl;
    private String selectedSurfaceWaterBaseUrl;
    private String selectedTerrainFixes;
    private BiomeIntegrationMode selectedBiomeIntegration;
    private boolean selectedCaves;
    private boolean selectedCanyons;
    private boolean selectedExtraUnderground;
    private boolean selectedAquifers;
    private boolean selectedLavaAquifers;
    private boolean selectedVillages;
    private boolean selectedWorldBorder;

    private CycleButton<Integer> zoomButton;
    private CycleButton<BiomeIntegrationMode> biomeIntegrationButton;
    private EditBox maxMountainYBox;
    private EditBox oceanFloorYBox;
    private EditBox spawnLatitudeBox;
    private EditBox spawnLongitudeBox;
    private EditBox terrainBaseUrlBox;
    private EditBox biomesBaseUrlBox;
    private EditBox surfaceWaterBaseUrlBox;
    private CycleButton<String> terrainFixesButton;
    private CycleButton<Boolean> cavesButton;
    private CycleButton<Boolean> villagesButton;
    private CycleButton<Boolean> canyonsButton;
    private CycleButton<Boolean> aquifersButton;
    private CycleButton<Boolean> extraUndergroundButton;
    private CycleButton<Boolean> lavaAquifersButton;
    private CycleButton<Boolean> worldBorderButton;
    private Button doneButton;
    private Component validationMessage;
    private ValidationKind validationKind = ValidationKind.NONE;

    private int fullWidth;
    private int halfWidth;
    private int leftX;
    private int rightX;
    private int scrollbarX;

    private int titleY;
    private int contentTop;
    private int viewportTop;
    private int viewportBottom;
    private int buttonY;

    private int zoomRowY;
    private int biomeIntegrationLabelY;
    private int biomeIntegrationRowY;
    private int worldSizeInfoY;
    private int scaleInfoY;
    private int heightValidationY;
    private int shapeLabelY;
    private int shapeRowY;
    private int spawnLabelY;
    private int spawnRowY;
    private int terrainUrlLabelY;
    private int terrainUrlRowY;
    private int biomesUrlLabelY;
    private int biomesUrlRowY;
    private int waterUrlLabelY;
    private int waterUrlRowY;
    private int terrainFixesLabelY;
    private int terrainFixesRowY;
    private int togglesHeaderY;
    private int toggleRow1Y;
    private int toggleRow2Y;
    private int toggleRow3Y;
    private int toggleRow4Y;
    private int contentHeight;

    private int scrollOffset;
    private int maxScroll;
    private boolean draggingScrollbar;

    private EarthPresetEditorScreen(CreateWorldScreen parent, PresetSettings initialSettings) {
        super(TITLE);
        this.parent = parent;
        this.biomesOPlentyLoaded = isModLoadedSafely("biomesoplenty");
        this.maxTerrainYLimit = initialSettings.maxTerrainYLimit();
        EarthGenConfig.setActiveMaxTerrainY(maxTerrainYLimit);
        this.selectedZoom = EarthGenConfig.validateZoom(initialSettings.zoom());
        this.selectedMaxMountainY = EarthGenConfig.validateMaxMountainY(initialSettings.maxMountainY(), maxTerrainYLimit);
        this.selectedOceanFloorY = EarthGenConfig.validateOceanFloorY(initialSettings.oceanFloorY());
        this.selectedSpawnLatitude = initialSettings.spawnLatitude();
        this.selectedSpawnLongitude = initialSettings.spawnLongitude();
        this.selectedTerrainBaseUrl = initialSettings.terrainBaseUrl();
        this.selectedBiomesBaseUrl = initialSettings.biomesBaseUrl();
        this.selectedSurfaceWaterBaseUrl = initialSettings.surfaceWaterBaseUrl();
        this.selectedTerrainFixes = initialSettings.terrainFixes();
        this.selectedBiomeIntegration = initialSettings.biomeIntegration();
        EarthWorldgenToggles toggles = initialSettings.worldgenToggles();
        this.selectedCaves = toggles.caves();
        this.selectedCanyons = toggles.canyons();
        this.selectedExtraUnderground = toggles.extraUnderground();
        this.selectedAquifers = toggles.aquifers();
        this.selectedLavaAquifers = toggles.lavaAquifers();
        this.selectedVillages = toggles.villages();
        this.selectedWorldBorder = initialSettings.worldBorder();
    }

    public static Screen create(CreateWorldScreen parent, WorldCreationContext context) {
        return new EarthPresetEditorScreen(parent, resolveCurrentSettings(context));
    }

    @Override
    protected void init() {
        layoutScreen();

        zoomButton = CycleButton.<Integer>builder(value -> Component.literal(Integer.toString(value)), Integer.valueOf(selectedZoom))
            .withValues(SUPPORTED_ZOOMS)
            .create(leftX, zoomRowY, fullWidth, ROW_HEIGHT, ZOOM_LABEL, (button, value) -> {
                selectedZoom = value.intValue();
                updateValidationState();
            });
        zoomButton.setValue(selectedZoom);
        addRenderableWidget(zoomButton);

        biomeIntegrationButton = CycleButton.<BiomeIntegrationMode>builder(this::biomeIntegrationModeLabel, selectedBiomeIntegration)
            .withValues(List.of(BiomeIntegrationMode.AUTO, BiomeIntegrationMode.VANILLA, BiomeIntegrationMode.EXPANDED))
            .create(leftX, biomeIntegrationRowY, halfWidth, ROW_HEIGHT, BIOME_INTEGRATION_LABEL, (button, value) -> {
                selectedBiomeIntegration = value;
                updateValidationState();
            });
        biomeIntegrationButton.setValue(selectedBiomeIntegration);
        addRenderableWidget(biomeIntegrationButton);

        maxMountainYBox = new EditBox(font, leftX, shapeRowY, halfWidth, ROW_HEIGHT, MAX_MOUNTAIN_LABEL);
        maxMountainYBox.setValue(Integer.toString(selectedMaxMountainY));
        maxMountainYBox.setResponder(ignored -> updateValidationState());
        addRenderableWidget(maxMountainYBox);

        oceanFloorYBox = new EditBox(font, rightX, shapeRowY, halfWidth, ROW_HEIGHT, OCEAN_DEPTH_LABEL);
        oceanFloorYBox.setValue(Integer.toString(selectedOceanFloorY));
        oceanFloorYBox.setResponder(ignored -> updateValidationState());
        addRenderableWidget(oceanFloorYBox);

        spawnLatitudeBox = new EditBox(font, leftX, spawnRowY, halfWidth, ROW_HEIGHT, SPAWN_LATITUDE_LABEL);
        spawnLatitudeBox.setValue(Double.toString(selectedSpawnLatitude));
        spawnLatitudeBox.setResponder(ignored -> updateValidationState());
        addRenderableWidget(spawnLatitudeBox);

        spawnLongitudeBox = new EditBox(font, rightX, spawnRowY, halfWidth, ROW_HEIGHT, SPAWN_LONGITUDE_LABEL);
        spawnLongitudeBox.setValue(Double.toString(selectedSpawnLongitude));
        spawnLongitudeBox.setResponder(ignored -> updateValidationState());
        addRenderableWidget(spawnLongitudeBox);

        terrainBaseUrlBox = new EditBox(font, leftX, terrainUrlRowY, fullWidth, ROW_HEIGHT, TERRAIN_BASE_URL_LABEL);
        terrainBaseUrlBox.setMaxLength(512);
        terrainBaseUrlBox.setValue(selectedTerrainBaseUrl);
        terrainBaseUrlBox.setResponder(ignored -> updateValidationState());
        addRenderableWidget(terrainBaseUrlBox);

        biomesBaseUrlBox = new EditBox(font, leftX, biomesUrlRowY, fullWidth, ROW_HEIGHT, BIOMES_BASE_URL_LABEL);
        biomesBaseUrlBox.setMaxLength(512);
        biomesBaseUrlBox.setValue(selectedBiomesBaseUrl);
        biomesBaseUrlBox.setResponder(ignored -> updateValidationState());
        addRenderableWidget(biomesBaseUrlBox);

        surfaceWaterBaseUrlBox = new EditBox(font, leftX, waterUrlRowY, fullWidth, ROW_HEIGHT, SURFACE_WATER_BASE_URL_LABEL);
        surfaceWaterBaseUrlBox.setMaxLength(512);
        surfaceWaterBaseUrlBox.setValue(selectedSurfaceWaterBaseUrl);
        surfaceWaterBaseUrlBox.setResponder(ignored -> updateValidationState());
        addRenderableWidget(surfaceWaterBaseUrlBox);

        terrainFixesButton = CycleButton.<String>builder(Component::literal, selectedTerrainFixes)
            .withValues(List.of(selectedTerrainFixes))
            .create(leftX, terrainFixesRowY, fullWidth, ROW_HEIGHT, TERRAIN_FIXES_LABEL, (button, value) -> selectedTerrainFixes = value);
        terrainFixesButton.active = false;
        addRenderableWidget(terrainFixesButton);

        cavesButton = createToggleButton(leftX, toggleRow1Y, halfWidth, CAVES_LABEL, selectedCaves, value -> selectedCaves = value);
        villagesButton = createToggleButton(rightX, toggleRow1Y, halfWidth, VILLAGES_LABEL, selectedVillages, value -> selectedVillages = value);
        canyonsButton = createToggleButton(leftX, toggleRow2Y, halfWidth, CANYONS_LABEL, selectedCanyons, value -> selectedCanyons = value);
        aquifersButton = createToggleButton(rightX, toggleRow2Y, halfWidth, AQUIFERS_LABEL, selectedAquifers, value -> selectedAquifers = value);
        extraUndergroundButton = createToggleButton(
            leftX,
            toggleRow3Y,
            halfWidth,
            EXTRA_UNDERGROUND_LABEL,
            selectedExtraUnderground,
            value -> selectedExtraUnderground = value
        );
        lavaAquifersButton = createToggleButton(
            rightX,
            toggleRow3Y,
            halfWidth,
            LAVA_AQUIFERS_LABEL,
            selectedLavaAquifers,
            value -> selectedLavaAquifers = value
        );
        worldBorderButton = createToggleButton(
            leftX,
            toggleRow4Y,
            halfWidth,
            WORLD_BORDER_LABEL,
            selectedWorldBorder,
            value -> selectedWorldBorder = value
        );
        addRenderableWidget(cavesButton);
        addRenderableWidget(villagesButton);
        addRenderableWidget(canyonsButton);
        addRenderableWidget(aquifersButton);
        addRenderableWidget(extraUndergroundButton);
        addRenderableWidget(lavaAquifersButton);
        addRenderableWidget(worldBorderButton);

        doneButton = addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE, button -> applyAndClose())
                .bounds(width / 2 - 102, buttonY, 100, 20)
                .build()
        );
        addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + 2, buttonY, 100, 20)
                .build()
        );

        scrollOffset = 0;
        draggingScrollbar = false;
        updateScrollLimits();
        refreshScrollableWidgetPositions();
        updateValidationState();
    }

    private void layoutScreen() {
        int horizontalPadding = 80;
        fullWidth = Math.min(560, Math.max(320, width - horizontalPadding));
        int gutter = 8;
        halfWidth = (fullWidth - gutter) / 2;
        leftX = (width - fullWidth) / 2;
        rightX = leftX + halfWidth + gutter;
        scrollbarX = leftX + fullWidth + 8;

        titleY = 14;
        contentTop = titleY + 22;
        buttonY = height - 28;
        viewportTop = contentTop;
        viewportBottom = buttonY - 8;

        int y = contentTop;
        zoomRowY = y;
        y += ROW_HEIGHT + ROW_GAP;
        biomeIntegrationLabelY = y;
        y += LABEL_GAP;
        biomeIntegrationRowY = y;
        y += ROW_HEIGHT + SECTION_GAP;
        worldSizeInfoY = y;
        y += 10;
        scaleInfoY = y;
        y += SECTION_GAP;
        heightValidationY = y;
        if (validationKind == ValidationKind.HEIGHT && validationMessage != null) {
            y += heightValidationBlockHeight(validationMessage) + ROW_GAP;
        }
        shapeLabelY = y;
        y += LABEL_GAP;
        shapeRowY = y;
        y += ROW_HEIGHT + SECTION_GAP;
        spawnLabelY = y;
        y += LABEL_GAP;
        spawnRowY = y;
        y += ROW_HEIGHT + SECTION_GAP;
        terrainUrlLabelY = y;
        y += LABEL_GAP;
        terrainUrlRowY = y;
        y += ROW_HEIGHT + ROW_GAP;
        biomesUrlLabelY = y;
        y += LABEL_GAP;
        biomesUrlRowY = y;
        y += ROW_HEIGHT + ROW_GAP;
        waterUrlLabelY = y;
        y += LABEL_GAP;
        waterUrlRowY = y;
        y += ROW_HEIGHT + SECTION_GAP;
        terrainFixesLabelY = y;
        y += LABEL_GAP;
        terrainFixesRowY = y;
        y += ROW_HEIGHT + SECTION_GAP;
        togglesHeaderY = y;
        y += LABEL_GAP;
        toggleRow1Y = y;
        y += ROW_HEIGHT + ROW_GAP;
        toggleRow2Y = y;
        y += ROW_HEIGHT + ROW_GAP;
        toggleRow3Y = y;
        y += ROW_HEIGHT + ROW_GAP;
        toggleRow4Y = y;
        contentHeight = (toggleRow4Y + ROW_HEIGHT) - contentTop;
    }

    private void updateScrollLimits() {
        int viewportHeight = Math.max(1, viewportBottom - viewportTop);
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollOffset = clamp(scrollOffset, 0, maxScroll);
    }

    private void refreshScrollableWidgetPositions() {
        refreshWidget(zoomButton, zoomRowY);
        refreshWidget(biomeIntegrationButton, biomeIntegrationRowY);
        refreshWidget(maxMountainYBox, shapeRowY);
        refreshWidget(oceanFloorYBox, shapeRowY);
        refreshWidget(spawnLatitudeBox, spawnRowY);
        refreshWidget(spawnLongitudeBox, spawnRowY);
        refreshWidget(terrainBaseUrlBox, terrainUrlRowY);
        refreshWidget(biomesBaseUrlBox, biomesUrlRowY);
        refreshWidget(surfaceWaterBaseUrlBox, waterUrlRowY);
        refreshWidget(terrainFixesButton, terrainFixesRowY);
        refreshWidget(cavesButton, toggleRow1Y);
        refreshWidget(villagesButton, toggleRow1Y);
        refreshWidget(canyonsButton, toggleRow2Y);
        refreshWidget(aquifersButton, toggleRow2Y);
        refreshWidget(extraUndergroundButton, toggleRow3Y);
        refreshWidget(lavaAquifersButton, toggleRow3Y);
        refreshWidget(worldBorderButton, toggleRow4Y);
        terrainFixesButton.active = false;
    }

    private void refreshWidget(AbstractWidget widget, int baseY) {
        if (widget == null) {
            return;
        }
        int y = scrolledY(baseY);
        widget.setY(y);
        boolean visible = y + ROW_HEIGHT >= viewportTop && y <= viewportBottom;
        widget.visible = visible;
        if (widget != terrainFixesButton) {
            widget.active = visible;
        }
    }

    private int scrolledY(int baseY) {
        return baseY - scrollOffset;
    }

    private CycleButton<Boolean> createToggleButton(
        int x,
        int y,
        int width,
        Component label,
        boolean initialValue,
        java.util.function.Consumer<Boolean> onUpdate
    ) {
        CycleButton<Boolean> button = CycleButton.<Boolean>builder(this::booleanValueLabel, Boolean.valueOf(initialValue))
            .withValues(List.of(Boolean.TRUE, Boolean.FALSE))
            .create(x, y, width, ROW_HEIGHT, label, (cycle, value) -> {
                onUpdate.accept(value);
                updateValidationState();
            });
        button.setValue(Boolean.valueOf(initialValue));
        return button;
    }

    private Component booleanValueLabel(Boolean value) {
        return value.booleanValue()
            ? Component.translatable("options.on")
            : Component.translatable("options.off");
    }

    private void updateValidationState() {
        ValidationKind previousKind = validationKind;
        int previousHeightValidationPixels = heightValidationBlockHeight(validationMessage);

        OptionalInt parsedMaxMountainY = parseMaxMountainY(maxMountainYBox.getValue());
        OptionalInt parsedOceanFloorY = parseOceanFloorY(oceanFloorYBox.getValue());
        OptionalDouble parsedSpawnLatitude = parseSpawnLatitude(spawnLatitudeBox.getValue());
        OptionalDouble parsedSpawnLongitude = parseSpawnLongitude(spawnLongitudeBox.getValue());
        if (parsedMaxMountainY.isEmpty() || parsedOceanFloorY.isEmpty()) {
            doneButton.active = false;
            validationMessage = invalidHeightInput();
            validationKind = ValidationKind.HEIGHT;
            reflowLayoutIfNeeded(previousKind, previousHeightValidationPixels);
            return;
        }
        if (parsedSpawnLatitude.isEmpty() || parsedSpawnLongitude.isEmpty()) {
            doneButton.active = false;
            validationMessage = INVALID_GEO_INPUT;
            validationKind = ValidationKind.GEO;
            reflowLayoutIfNeeded(previousKind, previousHeightValidationPixels);
            return;
        }

        selectedMaxMountainY = parsedMaxMountainY.getAsInt();
        selectedOceanFloorY = parsedOceanFloorY.getAsInt();
        selectedSpawnLatitude = parsedSpawnLatitude.getAsDouble();
        selectedSpawnLongitude = parsedSpawnLongitude.getAsDouble();
        selectedTerrainBaseUrl = terrainBaseUrlBox.getValue();
        selectedBiomesBaseUrl = biomesBaseUrlBox.getValue();
        selectedSurfaceWaterBaseUrl = surfaceWaterBaseUrlBox.getValue();
        try {
            new EarthGenerationProfile(
                selectedZoom,
                selectedMaxMountainY,
                selectedOceanFloorY,
                selectedTerrainBaseUrl,
                selectedBiomesBaseUrl,
                selectedSurfaceWaterBaseUrl,
                selectedTerrainFixes,
                selectedWorldgenToggles(),
                selectedWorldBorder,
                selectedSpawnLatitude,
                selectedSpawnLongitude
            );
            doneButton.active = true;
            validationMessage = null;
            validationKind = ValidationKind.NONE;
        } catch (IllegalArgumentException exception) {
            doneButton.active = false;
            validationMessage = INVALID_URL_INPUT;
            validationKind = ValidationKind.URL;
        }
        reflowLayoutIfNeeded(previousKind, previousHeightValidationPixels);
    }

    private void applyAndClose() {
        updateValidationState();
        if (!doneButton.active) {
            return;
        }

        parent.getUiState().updateDimensions((registryAccess, worldDimensions) -> {
            var currentGenerator = worldDimensions.overworld();
            if (!(currentGenerator instanceof NoiseBasedChunkGenerator noiseBased)) {
                return worldDimensions;
            }

            BiomeSource currentBiomeSource = noiseBased.getBiomeSource();
            if (!(currentBiomeSource instanceof EcoregionBiomeSource)) {
                return worldDimensions;
            }

            var biomeLookup = registryAccess.lookupOrThrow(Registries.BIOME);
            EcoregionBiomeSource updatedBiomeSource = new EcoregionBiomeSource(
                biomeLookup,
                new EarthGenerationProfile(
                    selectedZoom,
                    selectedMaxMountainY,
                    selectedOceanFloorY,
                    selectedTerrainBaseUrl,
                    selectedBiomesBaseUrl,
                    selectedSurfaceWaterBaseUrl,
                    selectedTerrainFixes,
                    selectedWorldgenToggles(),
                    selectedWorldBorder,
                    selectedSpawnLatitude,
                    selectedSpawnLongitude
                ),
                selectedBiomeIntegration
            );
            NoiseBasedChunkGenerator updatedGenerator = new NoiseBasedChunkGenerator(updatedBiomeSource, noiseBased.generatorSettings());
            return worldDimensions.replaceOverworldGenerator(registryAccess, updatedGenerator);
        });
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            int delta = (int) Math.round(-scrollY * 18.0);
            if (delta != 0) {
                setScrollOffset(scrollOffset + delta);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() == 0 && isOverScrollbar(event.x(), event.y())) {
            draggingScrollbar = true;
            updateScrollFromMouseY(event.y());
            return true;
        }
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar && event.button() == 0) {
            updateScrollFromMouseY(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingScrollbar && event.button() == 0) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private void setScrollOffset(int newOffset) {
        int clamped = clamp(newOffset, 0, maxScroll);
        if (clamped == scrollOffset) {
            return;
        }
        scrollOffset = clamped;
        refreshScrollableWidgetPositions();
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        if (maxScroll <= 0) {
            return false;
        }
        int barWidth = 6;
        return mouseX >= scrollbarX
            && mouseX < scrollbarX + barWidth
            && mouseY >= viewportTop
            && mouseY <= viewportBottom;
    }

    private void updateScrollFromMouseY(double mouseY) {
        if (maxScroll <= 0) {
            return;
        }
        int trackHeight = Math.max(1, viewportBottom - viewportTop);
        int thumbHeight = thumbHeight(trackHeight);
        int travel = Math.max(1, trackHeight - thumbHeight);
        double centered = mouseY - viewportTop - (thumbHeight / 2.0);
        double normalized = centered / (double) travel;
        normalized = Math.max(0.0, Math.min(1.0, normalized));
        setScrollOffset((int) Math.round(normalized * maxScroll));
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1.21.11 enforces at most one blur pass per frame. Using a plain dim fill
        // avoids double-blur crashes when the parent screen already requested blur.
        guiGraphics.fill(0, 0, width, height, 0xA0101010);

        int centerX = width / 2;
        int span = EarthGenConfig.blockSpanForZoom(selectedZoom);
        double metersPerBlock = EarthGenConfig.metersPerBlockForZoom(selectedZoom);

        guiGraphics.drawCenteredString(font, TITLE, centerX, titleY, withOpaqueAlpha(0xFFFFFF));

        drawLabelIfVisible(guiGraphics, BIOME_INTEGRATION_LABEL, leftX, biomeIntegrationLabelY, 0xCFCFCF);
        drawLabelIfVisible(guiGraphics, MAX_MOUNTAIN_LABEL, leftX, shapeLabelY, 0xCFCFCF);
        drawLabelIfVisible(guiGraphics, OCEAN_DEPTH_LABEL, rightX, shapeLabelY, 0xCFCFCF);
        drawLabelIfVisible(guiGraphics, SPAWN_LATITUDE_LABEL, leftX, spawnLabelY, 0xCFCFCF);
        drawLabelIfVisible(guiGraphics, SPAWN_LONGITUDE_LABEL, rightX, spawnLabelY, 0xCFCFCF);
        drawLabelIfVisible(guiGraphics, TERRAIN_BASE_URL_LABEL, leftX, terrainUrlLabelY, 0xCFCFCF);
        drawLabelIfVisible(guiGraphics, BIOMES_BASE_URL_LABEL, leftX, biomesUrlLabelY, 0xCFCFCF);
        drawLabelIfVisible(guiGraphics, SURFACE_WATER_BASE_URL_LABEL, leftX, waterUrlLabelY, 0xCFCFCF);
        drawLabelIfVisible(guiGraphics, TERRAIN_FIXES_LABEL, leftX, terrainFixesLabelY, 0xCFCFCF);
        drawCenteredIfVisible(
            guiGraphics,
            TOGGLES_HEADER,
            centerX,
            togglesHeaderY,
            0xCFCFCF
        );
        drawCenteredIfVisible(
            guiGraphics,
            Component.translatable("terrarium_expanded.customize.earth.world_size", span, span),
            centerX,
            worldSizeInfoY,
            0xAFAFAF
        );
        drawCenteredIfVisible(
            guiGraphics,
            Component.translatable("terrarium_expanded.customize.earth.block_ratio", formatDistanceLabel(metersPerBlock)),
            centerX,
            scaleInfoY,
            0xAFAFAF
        );
        if (selectedBiomeIntegration == BiomeIntegrationMode.EXPANDED && !biomesOPlentyLoaded) {
            drawLabelIfVisible(guiGraphics, BIOMES_O_PLENTY_MISSING_WARNING, rightX, biomeIntegrationRowY + 6, 0xFFAA4D);
        }

        renderScrollbar(guiGraphics);

        // Render widgets after static labels so controls appear on top.
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (validationMessage != null) {
            if (validationKind == ValidationKind.HEIGHT) {
                renderHeightValidation(guiGraphics, validationMessage);
            } else {
                renderFooterValidation(guiGraphics, validationMessage, centerX);
            }
        }
    }

    private void renderHeightValidation(GuiGraphics guiGraphics, Component message) {
        List<FormattedCharSequence> lines = font.split(message, fullWidth);
        int lineHeight = font.lineHeight + 1;
        int startY = scrolledY(heightValidationY);
        for (int i = 0; i < lines.size(); i++) {
            int lineY = startY + i * lineHeight;
            if (lineY >= viewportTop - 10 && lineY <= viewportBottom) {
                guiGraphics.drawString(font, lines.get(i), leftX, lineY, withOpaqueAlpha(0xFF6B6B));
            }
        }
    }

    private void renderFooterValidation(GuiGraphics guiGraphics, Component message, int centerX) {
        List<FormattedCharSequence> lines = font.split(message, fullWidth);
        int lineHeight = font.lineHeight + 1;
        int totalHeight = lines.size() * lineHeight;
        int startY = buttonY - totalHeight - 3;
        for (int i = 0; i < lines.size(); i++) {
            guiGraphics.drawCenteredString(font, lines.get(i), centerX, startY + i * lineHeight, withOpaqueAlpha(0xFF6B6B));
        }
    }

    private void drawLabelIfVisible(GuiGraphics guiGraphics, Component text, int x, int baseY, int color) {
        int y = scrolledY(baseY);
        if (y >= viewportTop - 10 && y <= viewportBottom) {
            guiGraphics.drawString(font, text, x, y, withOpaqueAlpha(color));
        }
    }

    private void drawCenteredIfVisible(GuiGraphics guiGraphics, Component text, int centerX, int baseY, int color) {
        int y = scrolledY(baseY);
        if (y >= viewportTop - 10 && y <= viewportBottom) {
            guiGraphics.drawCenteredString(font, text, centerX, y, withOpaqueAlpha(color));
        }
    }

    private static int withOpaqueAlpha(int rgbOrArgb) {
        return rgbOrArgb | 0xFF000000;
    }

    private void renderScrollbar(GuiGraphics guiGraphics) {
        if (maxScroll <= 0) {
            return;
        }
        int barWidth = 6;
        int trackHeight = Math.max(1, viewportBottom - viewportTop);
        int thumbHeight = thumbHeight(trackHeight);
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = viewportTop + (int) Math.round((scrollOffset / (double) maxScroll) * travel);
        guiGraphics.fill(scrollbarX, viewportTop, scrollbarX + barWidth, viewportBottom, 0x66000000);
        int thumbColor = draggingScrollbar ? 0xFFE0E0E0 : 0xFFC0C0C0;
        guiGraphics.fill(scrollbarX, thumbY, scrollbarX + barWidth, thumbY + thumbHeight, thumbColor);
    }

    private int thumbHeight(int trackHeight) {
        int viewportHeight = Math.max(1, viewportBottom - viewportTop);
        int thumb = (int) Math.round((viewportHeight / (double) (viewportHeight + maxScroll)) * trackHeight);
        return clamp(thumb, 24, trackHeight);
    }

    private EarthWorldgenToggles selectedWorldgenToggles() {
        return new EarthWorldgenToggles(
            selectedCaves,
            selectedCanyons,
            selectedExtraUnderground,
            selectedAquifers,
            selectedLavaAquifers,
            selectedVillages
        );
    }

    private OptionalInt parseMaxMountainY(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(EarthGenConfig.validateMaxMountainY(Integer.parseInt(raw.trim()), maxTerrainYLimit));
        } catch (IllegalArgumentException ignored) {
            return OptionalInt.empty();
        }
    }

    private Component invalidHeightInput() {
        return Component.translatable(
            "terrarium_expanded.customize.earth.invalid_height_input",
            EarthGenConfig.MIN_MAX_MOUNTAIN_Y,
            maxTerrainYLimit,
            EarthGenConfig.MIN_TERRAIN_Y,
            EarthGenConfig.MAX_OCEAN_FLOOR_Y
        );
    }

    private static OptionalInt parseOceanFloorY(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(EarthGenConfig.validateOceanFloorY(Integer.parseInt(raw.trim())));
        } catch (IllegalArgumentException ignored) {
            return OptionalInt.empty();
        }
    }

    private static OptionalDouble parseSpawnLatitude(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalDouble.empty();
        }
        try {
            double latitude = Double.parseDouble(raw.trim());
            if (!Double.isFinite(latitude)) {
                return OptionalDouble.empty();
            }
            if (latitude < -EarthGenConfig.MAX_MERCATOR_LATITUDE || latitude > EarthGenConfig.MAX_MERCATOR_LATITUDE) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(latitude);
        } catch (IllegalArgumentException ignored) {
            return OptionalDouble.empty();
        }
    }

    private static OptionalDouble parseSpawnLongitude(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalDouble.empty();
        }
        try {
            double longitude = Double.parseDouble(raw.trim());
            if (!Double.isFinite(longitude)) {
                return OptionalDouble.empty();
            }
            if (longitude < EarthGenConfig.MIN_LONGITUDE || longitude > EarthGenConfig.MAX_LONGITUDE) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(longitude);
        } catch (IllegalArgumentException ignored) {
            return OptionalDouble.empty();
        }
    }

    private static String formatDistanceLabel(double metersPerBlock) {
        if (metersPerBlock >= 1000.0) {
            return String.format(Locale.ROOT, "%.3f km", metersPerBlock / 1000.0);
        }
        return String.format(Locale.ROOT, "%.2f m", metersPerBlock);
    }

    private static PresetSettings resolveCurrentSettings(WorldCreationContext context) {
        var currentGenerator = context.selectedDimensions().overworld();
        if (!(currentGenerator instanceof NoiseBasedChunkGenerator noiseBased)) {
            return PresetSettings.defaults();
        }
        int maxTerrainYLimit = resolveMaxTerrainYLimit(context, noiseBased);
        EarthGenConfig.setActiveMaxTerrainY(maxTerrainYLimit);
        BiomeSource currentBiomeSource = noiseBased.getBiomeSource();
        if (currentBiomeSource instanceof EcoregionBiomeSource ecoregionBiomeSource) {
            int clampedMaxMountainY = Math.min(ecoregionBiomeSource.maxMountainY(), maxTerrainYLimit);
            return new PresetSettings(
                ecoregionBiomeSource.zoom(),
                clampedMaxMountainY,
                ecoregionBiomeSource.oceanFloorY(),
                ecoregionBiomeSource.terrainBaseUrl(),
                ecoregionBiomeSource.biomesBaseUrl(),
                ecoregionBiomeSource.surfaceWaterBaseUrl(),
                ecoregionBiomeSource.terrainFixes(),
                ecoregionBiomeSource.worldBorder(),
                ecoregionBiomeSource.spawnLatitude(),
                ecoregionBiomeSource.spawnLongitude(),
                ecoregionBiomeSource.biomeIntegration(),
                ecoregionBiomeSource.worldgenToggles(),
                maxTerrainYLimit
            );
        }
        return PresetSettings.defaults();
    }

    private static int resolveMaxTerrainYLimit(WorldCreationContext context, NoiseBasedChunkGenerator generator) {
        try {
            // Datapack dimension definitions override world preset dimensions at world creation time.
            // Prefer the datapack LevelStem registry for the most accurate build-height view in UI.
            Integer fromDatapack = maxTerrainYFromLevelStemRegistry(context.datapackDimensions());
            if (fromDatapack != null) {
                return fromDatapack.intValue();
            }

            Integer fromSelectedDimensions = context.selectedDimensions()
                .get(LevelStem.OVERWORLD)
                .map(EarthPresetEditorScreen::maxTerrainYFromLevelStem)
                .orElse(null);
            if (fromSelectedDimensions != null) {
                return fromSelectedDimensions.intValue();
            }

            return EarthGenConfig.maxTerrainYFromVerticalRange(
                generator.generatorSettings().value().noiseSettings().minY(),
                generator.generatorSettings().value().noiseSettings().height()
            );
        } catch (RuntimeException ignored) {
            return EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y;
        }
    }

    private static Integer maxTerrainYFromLevelStemRegistry(Registry<LevelStem> stems) {
        if (stems == null) {
            return null;
        }
        return stems.getOptional(LevelStem.OVERWORLD)
            .map(EarthPresetEditorScreen::maxTerrainYFromLevelStem)
            .orElse(null);
    }

    private static int maxTerrainYFromLevelStem(LevelStem stem) {
        DimensionType type = stem.type().value();
        return EarthGenConfig.maxTerrainYFromVerticalRange(type.minY(), type.height());
    }

    private static List<Integer> supportedZooms() {
        List<Integer> values = new ArrayList<>();
        for (int zoom = EarthGenConfig.MIN_ZOOM; zoom <= EarthGenConfig.MAX_ZOOM; zoom++) {
            values.add(zoom);
        }
        return List.copyOf(values);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private Component biomeIntegrationModeLabel(BiomeIntegrationMode mode) {
        return switch (mode) {
            case AUTO -> Component.translatable("terrarium_expanded.customize.earth.biome_integration.auto");
            case VANILLA -> Component.translatable("terrarium_expanded.customize.earth.biome_integration.vanilla");
            case EXPANDED -> Component.translatable("terrarium_expanded.customize.earth.biome_integration.biomes_o_plenty");
        };
    }

    private static boolean isModLoadedSafely(String modId) {
        try {
            return Platform.isModLoaded(modId);
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private int heightValidationBlockHeight(Component message) {
        if (message == null || font == null) {
            return 0;
        }
        int lineHeight = font.lineHeight + 1;
        return font.split(message, fullWidth).size() * lineHeight;
    }

    private void reflowLayoutIfNeeded(ValidationKind previousKind, int previousHeightValidationPixels) {
        int currentHeightValidationPixels = heightValidationBlockHeight(validationMessage);
        boolean heightBlockWasVisible = previousKind == ValidationKind.HEIGHT && previousHeightValidationPixels > 0;
        boolean heightBlockIsVisible = validationKind == ValidationKind.HEIGHT && currentHeightValidationPixels > 0;
        boolean heightBlockSizeChanged = previousHeightValidationPixels != currentHeightValidationPixels;
        if (!heightBlockWasVisible && !heightBlockIsVisible && !heightBlockSizeChanged) {
            return;
        }
        layoutScreen();
        updateScrollLimits();
        refreshScrollableWidgetPositions();
    }

    private enum ValidationKind {
        NONE,
        HEIGHT,
        URL,
        GEO
    }

    private record PresetSettings(
        int zoom,
        int maxMountainY,
        int oceanFloorY,
        String terrainBaseUrl,
        String biomesBaseUrl,
        String surfaceWaterBaseUrl,
        String terrainFixes,
        boolean worldBorder,
        double spawnLatitude,
        double spawnLongitude,
        BiomeIntegrationMode biomeIntegration,
        EarthWorldgenToggles worldgenToggles,
        int maxTerrainYLimit
    ) {
        private static final PresetSettings DEFAULT = new PresetSettings(
            EarthGenConfig.DEFAULT_ZOOM,
            EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y,
            EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y,
            EarthGenerationProfile.DEFAULT_TERRAIN_BASE_URL,
            EarthGenerationProfile.DEFAULT_BIOMES_BASE_URL,
            EarthGenerationProfile.DEFAULT_SURFACE_WATER_BASE_URL,
            EarthGenerationProfile.TERRAIN_FIXES_NONE,
            false,
            EarthGenerationProfile.DEFAULT_SPAWN_LATITUDE,
            EarthGenerationProfile.DEFAULT_SPAWN_LONGITUDE,
            BiomeIntegrationMode.AUTO,
            EarthWorldgenToggles.defaults(),
            EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y
        );

        private static PresetSettings defaults() {
            return DEFAULT;
        }
    }
}
