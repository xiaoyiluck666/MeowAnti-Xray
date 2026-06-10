package com.meowantixray.fabric;

import com.meowantixray.platform.LoadedModInfo;
import com.meowantixray.platform.LoaderType;
import com.meowantixray.platform.ModPlatform;
import com.meowantixray.platform.PlatformSupport;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
        return PlatformSupport.sortedLoadedMods(loaded);
    }

    @Override
    public boolean hasPermission(ServerPlayer player, String permissionNode) {
        if (!PlatformSupport.hasUsablePermissionRequest(player, permissionNode)) {
            return false;
        }
        return FabricPermissionsBridge.hasPermission(player, permissionNode);
    }

    private static final class FabricPermissionsBridge {
        private static final java.lang.reflect.Method CHECK_METHOD = findCheckMethod();

        private static boolean hasPermission(ServerPlayer player, String permissionNode) {
            if (CHECK_METHOD == null) {
                return false;
            }
            try {
                Object result = CHECK_METHOD.invoke(null, player, permissionNode, Boolean.FALSE);
                return result instanceof Boolean allowed && allowed;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }

        private static java.lang.reflect.Method findCheckMethod() {
            try {
                Class<?> api = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
                for (java.lang.reflect.Method method : api.getMethods()) {
                    if (!"check".equals(method.getName()) || method.getParameterCount() != 3) {
                        continue;
                    }
                    Class<?>[] parameters = method.getParameterTypes();
                    if (parameters[0].isAssignableFrom(ServerPlayer.class)
                        && parameters[1] == String.class
                        && parameters[2] == boolean.class) {
                        return method;
                    }
                }
            } catch (ClassNotFoundException ignored) {
            }
            return null;
        }
    }
}
