package com.meowconsole;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.meowconsole.compat.MinecraftCompat;
import com.meowconsole.platform.PlatformHelper;
import com.meowconsole.update.ModrinthUpdateChecker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;
import java.util.Objects;

public final class MeowServerCommands {
    private MeowServerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildAntiXrayCommand("antixray"));
        dispatcher.register(buildAntiXrayCommand("xray"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAntiXrayCommand(String name) {
        String commandName = Objects.requireNonNull(name, "commandName");
        LiteralArgumentBuilder<CommandSourceStack> debugCommand = debugCommand();

        return Commands.literal(commandName)
            .requires(MeowServerCommands::hasAdminPermission)
            .executes(context -> sendAntiXrayStatus(context.getSource()))
            .then(Commands.literal("status")
                .executes(context -> sendAntiXrayStatus(context.getSource())))
            .then(Commands.literal("reload")
                .executes(context -> reloadAntiXray(context.getSource())))
            .then(Commands.literal("profile")
                .executes(context -> sendAntiXrayProfile(context.getSource())))
            .then(stressCommand())
            .then(debugCommand);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> stressCommand() {
        RequiredArgumentBuilder<CommandSourceStack, Integer> passesArgument = commandArgument("passes", IntegerArgumentType.integer(1, 20))
            .executes(MeowServerCommands::stressAntiXray);
        RequiredArgumentBuilder<CommandSourceStack, Integer> radiusArgument = commandArgument("radius", IntegerArgumentType.integer(0, 16))
            .then(passesArgument);
        RequiredArgumentBuilder<CommandSourceStack, String> worldArgument = commandArgument("world", StringArgumentType.word())
            .then(radiusArgument);
        return Objects.requireNonNull(Commands.literal("stress"), "stress command").then(worldArgument);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> debugCommand() {
        RequiredArgumentBuilder<CommandSourceStack, Integer> zArgument = commandArgument("z", IntegerArgumentType.integer())
            .executes(MeowServerCommands::debugAntiXrayBlock);
        RequiredArgumentBuilder<CommandSourceStack, Integer> yArgument = commandArgument("y", IntegerArgumentType.integer())
            .then(zArgument);
        RequiredArgumentBuilder<CommandSourceStack, Integer> xArgument = commandArgument("x", IntegerArgumentType.integer())
            .then(yArgument);
        RequiredArgumentBuilder<CommandSourceStack, String> worldArgument = commandArgument("world", StringArgumentType.word())
            .then(xArgument);
        return Objects.requireNonNull(Commands.literal("debug"), "debug command").then(worldArgument);
    }

    private static <T> RequiredArgumentBuilder<CommandSourceStack, T> commandArgument(String name, ArgumentType<T> type) {
        String argumentName = Objects.requireNonNull(name, "argumentName");
        ArgumentType<T> argumentType = Objects.requireNonNull(type, "argumentType");
        return Objects.requireNonNull(Commands.argument(argumentName, argumentType), argumentName + " argument");
    }

    private static boolean hasAdminPermission(CommandSourceStack source) {
        return MinecraftCompat.hasCommandPermission(source, 4);
    }

    private static int sendAntiXrayStatus(CommandSourceStack source) {
        source.sendSuccess(
            () -> Component.literal("[Anti-Xray] runtime: loader=" + PlatformHelper.loaderType().modrinthLoaderId()
                + ", version=" + PlatformHelper.modVersion("meowantixray", "unknown")
                + ", minecraft=" + MinecraftCompat.currentMinecraftVersionId()),
            false
        );
        source.sendSuccess(
            () -> Component.literal("[Anti-Xray] config source: " + MeowConsoleMod.fakeOre().configLoadSummary()),
            false
        );
        source.sendSuccess(
            () -> Component.literal("[Anti-Xray] " + MeowConsoleMod.fakeOre().describeConfig()),
            false
        );
        String updateStatus = Objects.requireNonNull(ModrinthUpdateChecker.statusSummary(), "update status");
        if (source.getPlayer() != null) {
            ModrinthUpdateChecker.sendStatusTo(source.getPlayer());
        } else {
            source.sendSuccess(
                () -> Component.literal(updateStatus),
                false
            );
        }
        return 1;
    }

    private static int reloadAntiXray(CommandSourceStack source) {
        MeowConsoleMod.fakeOre().reloadConfig();
        source.sendSuccess(
            () -> Component.literal("[Anti-Xray] config source: " + MeowConsoleMod.fakeOre().configLoadSummary()),
            true
        );
        source.sendSuccess(
            () -> Component.literal("[Anti-Xray] config reloaded: " + MeowConsoleMod.fakeOre().describeConfig()),
            true
        );
        return 1;
    }

    private static int sendAntiXrayProfile(CommandSourceStack source) {
        source.sendSuccess(
            () -> Component.literal("[Anti-Xray] " + MeowConsoleMod.fakeOre().profileSummary()),
            false
        );
        return 1;
    }

    private static int stressAntiXray(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getServer() instanceof DedicatedServer server) || !server.isRunning()) {
            source.sendFailure(Component.literal("[Anti-Xray] server is not ready."));
            return 0;
        }

        String worldName = StringArgumentType.getString(context, "world");
        ServerLevel level = resolveWorld(server, worldName);
        if (level == null) {
            source.sendFailure(Component.literal("[Anti-Xray] world not found: " + worldName));
            return 0;
        }

        int radius = IntegerArgumentType.getInteger(context, "radius");
        int passes = IntegerArgumentType.getInteger(context, "passes");
        String result = MeowConsoleMod.fakeOre().stressChunkRewrite(level, 0, 0, radius, passes);
        source.sendSuccess(() -> Component.literal("[Anti-Xray] " + result), false);
        return 1;
    }

    private static int debugAntiXrayBlock(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getServer() instanceof DedicatedServer server) || !server.isRunning()) {
            source.sendFailure(Component.literal("[Anti-Xray] server is not ready."));
            return 0;
        }

        String worldName = StringArgumentType.getString(context, "world");
        ServerLevel level = resolveWorld(server, worldName);
        if (level == null) {
            source.sendFailure(Component.literal("[Anti-Xray] world not found: " + worldName));
            return 0;
        }

        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        String debug = MeowConsoleMod.fakeOre().debugBlock(level, new net.minecraft.core.BlockPos(x, y, z));
        source.sendSuccess(() -> Component.literal("[Anti-Xray] " + debug), false);
        return 1;
    }

    private static ServerLevel resolveWorld(DedicatedServer server, String worldName) {
        String query = worldName == null ? "" : worldName.strip().toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            return null;
        }

        if ("overworld".equals(query) || "world".equals(query) || "minecraft:overworld".equals(query)) {
            return server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        }
        if ("nether".equals(query) || "the_nether".equals(query) || "minecraft:the_nether".equals(query)) {
            return server.getLevel(net.minecraft.world.level.Level.NETHER);
        }
        if ("end".equals(query) || "the_end".equals(query) || "minecraft:the_end".equals(query)) {
            return server.getLevel(net.minecraft.world.level.Level.END);
        }

        ResourceKey<net.minecraft.world.level.Level> key = MinecraftCompat.tryLevelKey(query);
        if (key != null) {
            ServerLevel level = server.getLevel(key);
            if (level != null) {
                return level;
            }
        }

        for (ServerLevel level : server.getAllLevels()) {
            if (worldLabel(level.dimension()).equalsIgnoreCase(query) || level.dimension().toString().equalsIgnoreCase(query)) {
                return level;
            }
        }
        return null;
    }

    private static String worldLabel(ResourceKey<net.minecraft.world.level.Level> worldKey) {
        if (net.minecraft.world.level.Level.OVERWORLD.equals(worldKey)) {
            return "overworld";
        }
        if (net.minecraft.world.level.Level.NETHER.equals(worldKey)) {
            return "nether";
        }
        if (net.minecraft.world.level.Level.END.equals(worldKey)) {
            return "end";
        }
        return worldKey.toString();
    }
}
