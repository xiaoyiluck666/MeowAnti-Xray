package com.meowconsole.mixin;

import com.meowconsole.MeowConsoleMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(PlayerChunkSender.class)
abstract class PlayerChunkSenderMixin {
    @Inject(method = "sendChunk", at = @At("HEAD"), cancellable = true)
    private static void meowconsole$sendChunkWithAntiXray(ServerGamePacketListenerImpl connection, ServerLevel level, LevelChunk chunk, CallbackInfo ci) {
        ServerGamePacketListenerImpl safeConnection = Objects.requireNonNull(connection, "connection");
        ServerLevel safeLevel = Objects.requireNonNull(level, "level");
        LevelChunk safeChunk = Objects.requireNonNull(chunk, "chunk");
        MeowConsoleMod.fakeOre().sendChunkPacket(safeConnection, safeLevel, safeChunk);
        ci.cancel();
    }
}
