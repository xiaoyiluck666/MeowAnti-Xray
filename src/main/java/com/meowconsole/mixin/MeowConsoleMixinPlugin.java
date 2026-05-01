package com.meowconsole.mixin;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class MeowConsoleMixinPlugin implements IMixinConfigPlugin {
    private static final boolean SERVER_EXPLOSION = classExists("net.minecraft.world.level.ServerExplosion")
        || isInterface("net.minecraft.world.level.Explosion");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
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
        List<String> mixins = new ArrayList<>();
        mixins.add(SERVER_EXPLOSION ? "ServerExplosionMixin" : "LegacyServerExplosionMixin");
        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean classExists(String className) {
        return classResource(className) != null;
    }

    private static boolean hasMethod(String className, String methodName) {
        ClassNode node = readClassNode(className);
        if (node == null) {
            return false;
        }
        for (var method : node.methods) {
            if (method.name.equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInterface(String className) {
        ClassNode node = readClassNode(className);
        return node != null && (node.access & Opcodes.ACC_INTERFACE) != 0;
    }

    private static ClassNode readClassNode(String className) {
        String resourceName = classResource(className);
        if (resourceName == null) {
            return null;
        }
        try (InputStream stream = MeowConsoleMixinPlugin.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                return null;
            }
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String classResource(String className) {
        String resourceName = className.replace('.', '/') + ".class";
        return MeowConsoleMixinPlugin.class.getClassLoader().getResource(resourceName) == null ? null : resourceName;
    }

}
