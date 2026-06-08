package com.meowantixray.mixin;

import com.meowantixray.MeowAntiXrayMod;
import com.meowantixray.platform.LoaderType;
import com.meowantixray.platform.PlatformHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerChunkSender.class)
abstract class PlayerChunkSenderMixin {
    @Inject(method = "sendChunk", at = @At("HEAD"), cancellable = true)
    private static void meowconsole$sendNeoForgeChunkWithAntiXray(
        ServerGamePacketListenerImpl connection,
        ServerLevel level,
        LevelChunk chunk,
        CallbackInfo ci
    ) {
        if (shouldUseVanillaChunkPacketContext()) {
            return;
        }
        ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);
        MeowAntiXrayMod.fakeOre().sendChunkPacket(connection, level, chunk, packet);
        ci.cancel();
    }

    @Redirect(
        method = "sendChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private static void meowconsole$obfuscateChunkPacketBeforeSend(
        ServerGamePacketListenerImpl connection,
        Packet<?> packet,
        ServerGamePacketListenerImpl originalConnection,
        ServerLevel level,
        LevelChunk chunk
    ) {
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
            MeowAntiXrayMod.fakeOre().sendChunkPacket(connection, level, chunk, chunkPacket);
            return;
        }
        connection.send(packet);
    }

    private static boolean shouldUseVanillaChunkPacketContext() {
        return PlatformHelper.loaderType() == LoaderType.FABRIC
            && (
                PlatformHelper.isModLoaded("polymer-core")
                    || PlatformHelper.isModLoaded("polymer-bundled")
                    || PlatformHelper.isModLoaded("polymer")
            );
    }
}
