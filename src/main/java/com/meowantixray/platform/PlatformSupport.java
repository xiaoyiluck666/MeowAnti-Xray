package com.meowantixray.platform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;

public final class PlatformSupport {
    private PlatformSupport() {
    }

    public static List<LoadedModInfo> sortedLoadedMods(Collection<LoadedModInfo> mods) {
        List<LoadedModInfo> sorted = new ArrayList<>(mods);
        sorted.sort(Comparator.comparing(mod -> mod.id().toLowerCase(Locale.ROOT)));
        return List.copyOf(sorted);
    }

    public static boolean hasUsablePermissionRequest(ServerPlayer player, String permissionNode) {
        return player != null && permissionNode != null && !permissionNode.isBlank();
    }
}
