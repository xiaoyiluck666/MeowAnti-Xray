package com.meowconsole.platform;

import java.nio.file.Path;
import java.util.Collection;
import net.minecraft.server.level.ServerPlayer;

public final class PlatformHelper {
    private static volatile ModPlatform platform;

    private PlatformHelper() {
    }

    public static void bind(ModPlatform nextPlatform) {
        if (nextPlatform == null) {
            throw new IllegalArgumentException("platform");
        }
        platform = nextPlatform;
    }

    public static ModPlatform get() {
        ModPlatform current = platform;
        if (current == null) {
            throw new IllegalStateException("MeowConsole platform bridge has not been bound yet.");
        }
        return current;
    }

    public static LoaderType loaderType() {
        return get().loaderType();
    }

    public static Path configDir() {
        return get().configDir();
    }

    public static Path gameDir() {
        return get().gameDir();
    }

    public static boolean isModLoaded(String modId) {
        return get().isModLoaded(modId);
    }

    public static String modVersion(String modId, String fallback) {
        return get().modVersion(modId).orElse(fallback);
    }

    public static Collection<LoadedModInfo> loadedMods() {
        return get().loadedMods();
    }

    public static boolean hasPermission(ServerPlayer player, String permissionNode) {
        return get().hasPermission(player, permissionNode);
    }
}
