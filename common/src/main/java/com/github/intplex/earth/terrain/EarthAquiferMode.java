package com.github.intplex.earth.terrain;

public enum EarthAquiferMode {
    OFF("off", false, false),
    WATER_ONLY("water_only", true, false),
    WATER_AND_LAVA("water_and_lava", true, true);

    private final String serializedName;
    private final boolean aquifersEnabled;
    private final boolean lavaAquifersEnabled;

    EarthAquiferMode(String serializedName, boolean aquifersEnabled, boolean lavaAquifersEnabled) {
        this.serializedName = serializedName;
        this.aquifersEnabled = aquifersEnabled;
        this.lavaAquifersEnabled = lavaAquifersEnabled;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean aquifersEnabled() {
        return aquifersEnabled;
    }

    public boolean lavaAquifersEnabled() {
        return lavaAquifersEnabled;
    }

    public static EarthAquiferMode from(EarthWorldgenToggles toggles) {
        if (toggles == null || !toggles.aquifers()) {
            return OFF;
        }
        return toggles.lavaAquifers() ? WATER_AND_LAVA : WATER_ONLY;
    }
}
