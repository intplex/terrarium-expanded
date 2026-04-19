package com.github.intplex.earth;

import com.github.intplex.earth.biome.EcoregionBiomeSource;
import com.github.intplex.earth.terrain.TerrainService;
import com.github.intplex.earth.terrain.WaterBodyKind;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class EarthCommands {
    private static final SimpleCommandExceptionType INVALID_COORDINATES = new SimpleCommandExceptionType(
        Component.literal("Latitude/longitude is outside the Terrarium bounds.")
    );
    private static final SimpleCommandExceptionType Y_OUT_OF_BOUNDS = new SimpleCommandExceptionType(
        Component.literal("Y is outside the world build bounds.")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildTpLatLongCommand("tplatlong"));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildTpLatLongCommand(String literal) {
        return Commands.literal(literal)
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.argument("latitude", DoubleArgumentType.doubleArg(-EarthGenConfig.MAX_MERCATOR_LATITUDE, EarthGenConfig.MAX_MERCATOR_LATITUDE))
                .then(Commands.argument("longitude", DoubleArgumentType.doubleArg(EarthGenConfig.MIN_LONGITUDE, EarthGenConfig.MAX_LONGITUDE))
                    .executes(context -> teleportToLatLong(
                        context.getSource(),
                        List.of(context.getSource().getPlayerOrException()),
                        DoubleArgumentType.getDouble(context, "latitude"),
                        DoubleArgumentType.getDouble(context, "longitude"),
                        null
                    ))
                    .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                        .executes(context -> teleportToLatLong(
                            context.getSource(),
                            List.of(context.getSource().getPlayerOrException()),
                            DoubleArgumentType.getDouble(context, "latitude"),
                            DoubleArgumentType.getDouble(context, "longitude"),
                            DoubleArgumentType.getDouble(context, "y")
                        ))
                    )
                )
            )
            .then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.argument("latitude", DoubleArgumentType.doubleArg(-EarthGenConfig.MAX_MERCATOR_LATITUDE, EarthGenConfig.MAX_MERCATOR_LATITUDE))
                    .then(Commands.argument("longitude", DoubleArgumentType.doubleArg(EarthGenConfig.MIN_LONGITUDE, EarthGenConfig.MAX_LONGITUDE))
                        .executes(context -> teleportToLatLong(
                            context.getSource(),
                            EntityArgument.getPlayers(context, "targets"),
                            DoubleArgumentType.getDouble(context, "latitude"),
                            DoubleArgumentType.getDouble(context, "longitude"),
                            null
                        ))
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                            .executes(context -> teleportToLatLong(
                                context.getSource(),
                                EntityArgument.getPlayers(context, "targets"),
                                DoubleArgumentType.getDouble(context, "latitude"),
                                DoubleArgumentType.getDouble(context, "longitude"),
                                DoubleArgumentType.getDouble(context, "y")
                            ))
                        )
                    )
                )
            );
    }

    private static int teleportToLatLong(
        CommandSourceStack source,
        Collection<ServerPlayer> targets,
        double latitude,
        double longitude,
        Double yOverride
    ) throws CommandSyntaxException {
        EarthGenConfig.BlockCoordinates target = EarthGenConfig.geoToBlock(latitude, longitude)
            .orElseThrow(INVALID_COORDINATES::create);

        int teleportedCount = targets.size();
        for (ServerPlayer player : targets) {
            ServerLevel level = player.level();
            double targetX = target.x() + 0.5;
            double targetZ = target.z() + 0.5;
            double targetY = resolveTargetY(level, target.x(), target.z(), yOverride);
            player.teleportTo(targetX, targetY, targetZ);
        }

        source.sendSuccess(
            () -> Component.literal(
                String.format(
                    Locale.ROOT,
                    "Teleported %d player(s) to lat %.5f lon %.5f at block %d (%s) %d",
                    teleportedCount,
                    latitude,
                    longitude,
                    target.x(),
                    yOverride == null ? "auto y (estimated surface/sea)" : "y=" + String.format(Locale.ROOT, "%.2f", yOverride),
                    target.z()
                )
            ),
            false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static double resolveTargetY(ServerLevel level, int blockX, int blockZ, Double yOverride) throws CommandSyntaxException {
        int minY = level.getMinY() + 1;
        int maxY = level.getMaxY() - 1;
        if (yOverride != null) {
            if (yOverride < minY || yOverride > maxY) {
                throw Y_OUT_OF_BOUNDS.create();
            }
            return yOverride;
        }

        int seaY = level.getChunkSource().getGenerator().getSeaLevel();
        if (isEarthOverworld(level)) {
            int estimatedSolidTopY = TerrainService.effectiveSolidTopYAtXZ(blockX, blockZ);
            int waterSurfaceY = TerrainService.inlandWaterSurfaceYAtXZ(blockX, blockZ);
            WaterBodyKind waterKind = TerrainService.inlandWaterKindAtXZ(blockX, blockZ);
            int estimatedSurfaceY = waterKind == WaterBodyKind.NONE
                ? Math.max(estimatedSolidTopY, seaY)
                : Math.max(Math.max(estimatedSolidTopY, waterSurfaceY), seaY);
            return clamp(estimatedSurfaceY + 1, minY, maxY);
        }

        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        return clamp(Math.max(surfaceY + 1, seaY + 1), minY, maxY);
    }

    private static boolean isEarthOverworld(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            return false;
        }
        BiomeSource biomeSource = noiseGenerator.getBiomeSource();
        return biomeSource instanceof EcoregionBiomeSource;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
