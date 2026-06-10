package com.meowantixray.neoforge;

import com.meowantixray.compat.MinecraftCompat;
import com.meowantixray.platform.LoadedModInfo;
import com.meowantixray.platform.LoaderType;
import com.meowantixray.platform.ModPlatform;
import com.meowantixray.platform.PlatformSupport;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
        return PlatformSupport.sortedLoadedMods(loaded);
    }

    @Override
    public boolean hasPermission(ServerPlayer player, String permissionNode) {
        if (!PlatformSupport.hasUsablePermissionRequest(player, permissionNode)) {
            return false;
        }
        return NeoForgePermissionsBridge.hasPermission(player, permissionNode);
    }

    private static final class NeoForgePermissionsBridge {
        private static final java.lang.reflect.Method PERMISSION_API_GET = findPermissionApiGet();
        private static final java.lang.reflect.Method PERMISSION_NODE_GET = findPermissionNodeGet();

        private static boolean hasPermission(ServerPlayer player, String permissionNode) {
            Boolean apiResult = checkPermissionApi(player, permissionNode);
            if (apiResult != null) {
                return apiResult;
            }
            return checkPlayerMethod(player, permissionNode);
        }

        private static Boolean checkPermissionApi(ServerPlayer player, String permissionNode) {
            if (PERMISSION_API_GET == null || PERMISSION_NODE_GET == null) {
                return null;
            }
            try {
                Object node = PERMISSION_NODE_GET.invoke(null, permissionNode);
                Object result = PERMISSION_API_GET.invoke(null, player, node, Boolean.FALSE);
                return result instanceof Boolean allowed ? allowed : null;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private static boolean checkPlayerMethod(ServerPlayer player, String permissionNode) {
            for (java.lang.reflect.Method method : player.getClass().getMethods()) {
                if (!"hasPermission".equals(method.getName()) || method.getParameterCount() != 1 || method.getParameterTypes()[0] != String.class) {
                    continue;
                }
                try {
                    Object result = method.invoke(player, permissionNode);
                    return result instanceof Boolean allowed && allowed;
                } catch (ReflectiveOperationException ignored) {
                    return false;
                }
            }
            return false;
        }

        private static java.lang.reflect.Method findPermissionApiGet() {
            try {
                Class<?> api = Class.forName("net.neoforged.neoforge.server.permission.PermissionAPI");
                for (java.lang.reflect.Method method : api.getMethods()) {
                    if (!"getPermission".equals(method.getName()) || method.getParameterCount() != 3) {
                        continue;
                    }
                    Class<?>[] parameters = method.getParameterTypes();
                    if (parameters[0].isAssignableFrom(ServerPlayer.class) && parameters[2] == boolean.class) {
                        return method;
                    }
                }
            } catch (ClassNotFoundException ignored) {
            }
            return null;
        }

        private static java.lang.reflect.Method findPermissionNodeGet() {
            try {
                Class<?> node = Class.forName("net.neoforged.neoforge.server.permission.nodes.PermissionNode");
                for (java.lang.reflect.Method method : node.getMethods()) {
                    if (java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && "get".equals(method.getName())
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == String.class) {
                        return method;
                    }
                }
            } catch (ClassNotFoundException ignored) {
            }
            return null;
        }
    }
}
