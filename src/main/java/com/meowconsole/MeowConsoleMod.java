package com.meowconsole;

import com.meowconsole.antixray.FakeOreService;
import com.meowconsole.compat.MinecraftCompat;
import com.meowconsole.platform.ModPlatform;
import com.meowconsole.platform.PlatformHelper;
import com.meowconsole.update.ModrinthUpdateChecker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MeowConsoleMod {
    public static final Logger LOGGER = LoggerFactory.getLogger("meowantixray");
    private static final String AUTHOR = "xiaoyiluck";
    private static final FakeOreService FAKE_ORE = new FakeOreService();
    private static volatile DedicatedServer CURRENT_SERVER;
    private static volatile boolean initialized;

    private MeowConsoleMod() {
    }

    public static FakeOreService fakeOre() {
        return FAKE_ORE;
    }

    public static DedicatedServer currentServer() {
        return CURRENT_SERVER;
    }

    public static synchronized void initialize(ModPlatform platform) {
        PlatformHelper.bind(platform);
        if (initialized) {
            return;
        }
        initialized = true;
        LOGGER.info("Meow Anti-Xray initialized. Author: {}", AUTHOR);
    }

    public static void onServerStarted(MinecraftServer server) {
        if (server instanceof DedicatedServer dedicatedServer) {
            CURRENT_SERVER = dedicatedServer;
        }
        FAKE_ORE.reloadConfig();
        LOGGER.info("Meow Anti-Xray config source: {}", FAKE_ORE.configLoadSummary());
        LOGGER.info("Meow Anti-Xray enabled: {}", FAKE_ORE.describeConfig());
        LOGGER.info("Meow Anti-Xray startup complete. Author: {}", AUTHOR);
        ModrinthUpdateChecker.checkAsync();
    }

    public static void onServerStopping(MinecraftServer server) {
        FAKE_ORE.shutdownAsyncExecutor();
        ModrinthUpdateChecker.shutdown();
        CURRENT_SERVER = null;
    }

    public static void onServerTick(MinecraftServer server) {
        FAKE_ORE.onServerTick();
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        FAKE_ORE.onPlayerDisconnect(player);
    }

    public static void onPlayerBreakBlock(ServerLevel level, ServerPlayer player, BlockPos pos) {
        FAKE_ORE.onBlockBroken(player, level, pos);
    }

    public static boolean isAdminLikePlayer(ServerPlayer player) {
        return MinecraftCompat.hasCommandPermission(player, 4);
    }
}
