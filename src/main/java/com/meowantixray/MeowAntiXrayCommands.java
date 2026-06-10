package com.meowantixray;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.meowantixray.compat.MinecraftCompat;
import com.meowantixray.platform.PlatformHelper;
import com.meowantixray.update.ModrinthUpdateChecker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class MeowAntiXrayCommands {
    private MeowAntiXrayCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildAntiXrayCommand("antixray"));
        dispatcher.register(buildAntiXrayCommand("xray"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAntiXrayCommand(String name) {
        String commandName = Objects.requireNonNull(name, "commandName");
        LiteralArgumentBuilder<CommandSourceStack> debugCommand = inspectCommand("debug");
        LiteralArgumentBuilder<CommandSourceStack> inspectCommand = inspectCommand("inspect");

        return Commands.literal(commandName)
            .requires(MeowAntiXrayCommands::hasAdminPermission)
            .executes(context -> sendAntiXrayStatus(context.getSource()))
            .then(Commands.literal("status")
                .executes(context -> sendAntiXrayStatus(context.getSource())))
            .then(Commands.literal("reload")
                .executes(context -> reloadAntiXray(context.getSource())))
            .then(Commands.literal("profile")
                .executes(context -> sendAntiXrayProfile(context.getSource())))
            .then(stressCommand())
            .then(debugCommand)
            .then(inspectCommand);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> stressCommand() {
        RequiredArgumentBuilder<CommandSourceStack, Integer> passesArgument = commandArgument("passes", IntegerArgumentType.integer(1, 20))
            .executes(MeowAntiXrayCommands::stressAntiXray);
        RequiredArgumentBuilder<CommandSourceStack, Integer> radiusArgument = commandArgument("radius", IntegerArgumentType.integer(0, 16))
            .then(passesArgument);
        RequiredArgumentBuilder<CommandSourceStack, String> worldArgument = commandArgument("world", StringArgumentType.word())
            .then(radiusArgument);
        return Objects.requireNonNull(Commands.literal("stress"), "stress command").then(worldArgument);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> inspectCommand(String name) {
        RequiredArgumentBuilder<CommandSourceStack, Integer> zArgument = commandArgument("z", IntegerArgumentType.integer())
            .executes(MeowAntiXrayCommands::inspectAntiXrayBlock);
        RequiredArgumentBuilder<CommandSourceStack, Integer> yArgument = commandArgument("y", IntegerArgumentType.integer())
            .then(zArgument);
        RequiredArgumentBuilder<CommandSourceStack, Integer> xArgument = commandArgument("x", IntegerArgumentType.integer())
            .then(yArgument);
        RequiredArgumentBuilder<CommandSourceStack, String> worldArgument = commandArgument("world", StringArgumentType.word())
            .then(xArgument);
        return Objects.requireNonNull(Commands.literal(name), name + " command").then(worldArgument);
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
        List<String> lines = new ArrayList<>();
        lines.add("runtime: loader=" + PlatformHelper.loaderType().modrinthLoaderId()
            + ", version=" + PlatformHelper.modVersion("meowantixray", "unknown")
            + ", minecraft=" + MinecraftCompat.currentMinecraftVersionId());
        lines.add("config source: " + MeowAntiXrayMod.fakeOre().configLoadSummary());
        lines.addAll(MeowAntiXrayMod.fakeOre().statusDetails());
        String updateStatus = Objects.requireNonNull(ModrinthUpdateChecker.statusSummary(), "update status");
        if (source.getPlayer() != null) {
            sendCommandLines(source, lines, false);
            ModrinthUpdateChecker.sendStatusTo(source.getPlayer());
        } else {
            lines.add(stripPrefix(updateStatus));
            sendCommandLines(source, lines, false);
        }
        return 1;
    }

    private static int reloadAntiXray(CommandSourceStack source) {
        var report = MeowAntiXrayMod.fakeOre().reloadConfigWithSummary();
        List<String> lines = new ArrayList<>();
        lines.add("config source: " + report.configLoadSummary());
        lines.addAll(report.statusLines());
        lines.addAll(report.changeLines());
        sendCommandLines(source, lines, true);
        return 1;
    }

    private static int sendAntiXrayProfile(CommandSourceStack source) {
        source.sendSuccess(
            () -> Component.literal("[Anti-Xray] " + MeowAntiXrayMod.fakeOre().profileSummary()),
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
        String result = MeowAntiXrayMod.fakeOre().stressChunkRewrite(level, 0, 0, radius, passes);
        source.sendSuccess(() -> Component.literal("[Anti-Xray] " + result), false);
        return 1;
    }

    private static int inspectAntiXrayBlock(CommandContext<CommandSourceStack> context) {
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
        sendCommandLines(
            source,
            MeowAntiXrayMod.fakeOre().inspectBlockDetails(level, new net.minecraft.core.BlockPos(x, y, z)),
            false
        );
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
            String dimensionId = worldId(level.dimension());
            if (worldLabel(level.dimension()).equalsIgnoreCase(query) || dimensionId.equalsIgnoreCase(query)) {
                return level;
            }
        }
        return null;
    }

    private static void sendCommandLines(CommandSourceStack source, List<String> lines, boolean broadcastToOps) {
        List<String> safeLines = lines.stream()
            .filter(Objects::nonNull)
            .map(String::strip)
            .filter(line -> !line.isEmpty())
            .toList();
        if (safeLines.isEmpty()) {
            return;
        }
        if (source.getPlayer() == null) {
            source.sendSuccess(
                () -> Component.literal("[Anti-Xray] " + formatCompactCommandOutput(safeLines)),
                broadcastToOps
            );
            return;
        }
        for (String line : safeLines) {
            String message = line;
            source.sendSuccess(() -> Component.literal("[Anti-Xray] " + message), broadcastToOps);
        }
    }

    static String formatCompactCommandOutput(List<String> lines) {
        return lines.stream()
            .filter(Objects::nonNull)
            .map(String::strip)
            .filter(line -> !line.isEmpty())
            .reduce((left, right) -> left + " || " + right)
            .orElse("");
    }

    private static String stripPrefix(String line) {
        String value = Objects.requireNonNullElse(line, "").strip();
        return value.startsWith("[Anti-Xray] ") ? value.substring("[Anti-Xray] ".length()) : value;
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
        return worldId(worldKey);
    }

    private static String worldId(ResourceKey<net.minecraft.world.level.Level> worldKey) {
        String raw = worldKey.toString();
        if (raw.startsWith("ResourceKey[")) {
            int slashIndex = raw.indexOf('/');
            int endIndex = raw.lastIndexOf(']');
            if (slashIndex >= 0 && endIndex > slashIndex) {
                return raw.substring(slashIndex + 1, endIndex).trim();
            }
        }
        return raw;
    }
}
