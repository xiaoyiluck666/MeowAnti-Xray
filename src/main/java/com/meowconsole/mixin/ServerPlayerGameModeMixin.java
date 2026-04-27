package com.meowconsole.mixin;

import com.meowconsole.MeowConsoleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"))
    private void meowconsole$onStartDestroy(
        BlockPos pos,
        ServerboundPlayerActionPacket.Action action,
        Direction direction,
        int worldHeight,
        int sequence,
        CallbackInfo ci
    ) {
        if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK && player.level() instanceof ServerLevel serverLevel) {
            MeowConsoleMod.fakeOre().onBlockBreakStarted(player, serverLevel, pos);
        }
    }
}
