package com.meowconsole.fabric;

import com.meowconsole.platform.LoadedModInfo;
import com.meowconsole.platform.LoaderType;
import com.meowconsole.platform.ModPlatform;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class FabricModPlatform implements ModPlatform {
    @Override
    public LoaderType loaderType() {
        return LoaderType.FABRIC;
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Optional<String> modVersion(String modId) {
        return FabricLoader.getInstance()
            .getModContainer(modId)
            .map(container -> container.getMetadata().getVersion().getFriendlyString());
    }

    @Override
    public Collection<LoadedModInfo> loadedMods() {
        Path modsDir = configDir().resolveSibling("mods").normalize();
        List<LoadedModInfo> loaded = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            Path first = null;
            ModOrigin origin = mod.getOrigin();
            if (origin.getKind() == ModOrigin.Kind.PATH) {
                for (Path path : origin.getPaths()) {
                    Path normalized = path.toAbsolutePath().normalize();
                    if (first == null) {
                        first = normalized;
                    }
                }
            }
            boolean userProvided = first != null && first.startsWith(modsDir);
            loaded.add(new LoadedModInfo(
                mod.getMetadata().getId(),
                mod.getMetadata().getVersion().getFriendlyString(),
                first,
                userProvided
            ));
        }
        loaded.sort((left, right) -> left.id().toLowerCase(Locale.ROOT).compareTo(right.id().toLowerCase(Locale.ROOT)));
        return loaded;
    }
}
