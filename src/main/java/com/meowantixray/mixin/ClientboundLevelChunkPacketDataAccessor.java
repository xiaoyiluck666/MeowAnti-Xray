package com.meowantixray.mixin;

import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundLevelChunkPacketData.class)
public interface ClientboundLevelChunkPacketDataAccessor {
    @Accessor("buffer")
    byte[] meowconsole$getBuffer();

    @Mutable
    @Accessor("buffer")
    void meowconsole$setBuffer(byte[] buffer);
}
