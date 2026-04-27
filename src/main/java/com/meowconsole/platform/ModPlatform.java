package com.meowconsole.platform;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

public interface ModPlatform {
    LoaderType loaderType();

    Path configDir();

    Path gameDir();

    boolean isModLoaded(String modId);

    Optional<String> modVersion(String modId);

    Collection<LoadedModInfo> loadedMods();
}
