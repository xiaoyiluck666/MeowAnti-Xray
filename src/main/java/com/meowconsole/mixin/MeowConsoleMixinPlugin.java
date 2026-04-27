package com.meowconsole.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class MeowConsoleMixinPlugin implements IMixinConfigPlugin {
    private static final boolean LEGACY_SERVER_EXPLOSION = !classExists("net.minecraft.world.level.ServerExplosion");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("ServerExplosionMixin")) {
            return !LEGACY_SERVER_EXPLOSION;
        }
        if (mixinClassName.endsWith("LegacyServerExplosionMixin")) {
            return LEGACY_SERVER_EXPLOSION;
        }
        if (mixinClassName.endsWith("PlayerChunkSenderMixin")) {
            return hasMethod("net.minecraft.server.network.PlayerChunkSender", "sendChunk");
        }
        if (mixinClassName.endsWith("ServerPlayerGameModeMixin")) {
            return hasMethod("net.minecraft.server.level.ServerPlayerGameMode", "handleBlockBreakAction");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, MeowConsoleMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static boolean hasMethod(String className, String methodName) {
        try {
            Class<?> type = Class.forName(className, false, MeowConsoleMixinPlugin.class.getClassLoader());
            for (var method : type.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    return true;
                }
            }
            return false;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

}
