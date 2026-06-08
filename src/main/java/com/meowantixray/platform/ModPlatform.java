package com.meowantixray.platform;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

public interface ModPlatform {
    LoaderType loaderType();

    Path configDir();

    Path gameDir();

    boolean isModLoaded(String modId);

    Optional<String> modVersion(String modId);

    Collection<LoadedModInfo> loadedMods();

    default boolean hasPermission(ServerPlayer player, String permissionNode) {
        return false;
    }
}
