package com.meowconsole.neoforge;

import com.meowconsole.compat.MinecraftCompat;
import com.meowconsole.platform.LoadedModInfo;
import com.meowconsole.platform.LoaderType;
import com.meowconsole.platform.ModPlatform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class NeoForgeModPlatform implements ModPlatform {
    @Override
    public LoaderType loaderType() {
        return LoaderType.NEOFORGE;
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        if ("minecraft".equals(modId)) {
            return true;
        }
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Optional<String> modVersion(String modId) {
        if ("minecraft".equals(modId)) {
            return Optional.of(MinecraftCompat.currentMinecraftVersionId());
        }
        return ModList.get()
            .getModContainerById(modId)
            .map(container -> container.getModInfo().getVersion().toString());
    }

    @Override
    public Collection<LoadedModInfo> loadedMods() {
        Path modsDir = gameDir().resolve("mods").toAbsolutePath().normalize();
        List<LoadedModInfo> loaded = new ArrayList<>();
        for (var mod : ModList.get().getMods()) {
            Path origin = null;
            try {
                origin = mod.getOwningFile().getFile().getFilePath().toAbsolutePath().normalize();
            } catch (Throwable ignored) {
            }
            boolean userProvided = origin != null && origin.startsWith(modsDir);
            loaded.add(new LoadedModInfo(
                mod.getModId(),
                mod.getVersion().toString(),
                origin,
                userProvided
            ));
        }
        loaded.sort((left, right) -> left.id().toLowerCase(Locale.ROOT).compareTo(right.id().toLowerCase(Locale.ROOT)));
        return loaded;
    }
}
