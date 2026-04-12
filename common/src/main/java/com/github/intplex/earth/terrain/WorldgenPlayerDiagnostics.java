package com.github.intplex.earth.terrain;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class WorldgenPlayerDiagnostics {
    private static volatile PlayerSnapshot latest = PlayerSnapshot.NONE;

    private WorldgenPlayerDiagnostics() {
    }

    public static void updateFromServer(MinecraftServer server) {
        if (server == null) {
            latest = PlayerSnapshot.NONE;
            return;
        }

        ServerLevel overworld = server.overworld();
        if (overworld == null || overworld.players().isEmpty()) {
            latest = PlayerSnapshot.NONE;
            return;
        }

        ServerPlayer player = overworld.players().get(0);
        latest = new PlayerSnapshot(
            true,
            player.getScoreboardName(),
            player.getBlockX(),
            player.getBlockY(),
            player.getBlockZ()
        );
    }

    public static String currentPlayerLabel() {
        PlayerSnapshot snapshot = latest;
        if (!snapshot.available()) {
            return "player=<none>";
        }
        return "player=" + snapshot.name()
            + " playerBlockX=" + snapshot.blockX()
            + " playerBlockY=" + snapshot.blockY()
            + " playerBlockZ=" + snapshot.blockZ();
    }

    public static void clear() {
        latest = PlayerSnapshot.NONE;
    }

    private record PlayerSnapshot(boolean available, String name, int blockX, int blockY, int blockZ) {
        private static final PlayerSnapshot NONE = new PlayerSnapshot(false, "", 0, 0, 0);
    }
}
