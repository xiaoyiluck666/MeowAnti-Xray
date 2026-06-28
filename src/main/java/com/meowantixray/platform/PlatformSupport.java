package com.meowantixray.platform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public final class PlatformSupport {
    private PlatformSupport() {
    }

    public static List<@NonNull LoadedModInfo> sortedLoadedMods(List<@NonNull LoadedModInfo> mods) {
        List<@NonNull LoadedModInfo> sorted = new ArrayList<>(mods);
        sorted.sort(Comparator.comparing(mod -> mod.id().toLowerCase(Locale.ROOT)));
        return List.copyOf(sorted);
    }

    public static boolean hasUsablePermissionRequest(@Nullable ServerPlayer player, @Nullable String permissionNode) {
        return player != null && permissionNode != null && !permissionNode.isBlank();
    }
}
