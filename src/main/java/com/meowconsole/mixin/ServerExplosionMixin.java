package com.meowconsole.mixin;

import com.meowconsole.MeowConsoleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@Mixin(targets = "net.minecraft.world.level.ServerExplosion")
abstract class ServerExplosionMixin {
    @Shadow
    public abstract ServerLevel level();

    @Inject(
        method = "explode",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerExplosion;interactWithBlocks(Ljava/util/List;)V",
            shift = At.Shift.AFTER
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void meowconsole$afterInteractWithBlocks(
        CallbackInfoReturnable<Integer> cir,
        List<BlockPos> explodedPositions,
        net.minecraft.util.profiling.ProfilerFiller profiler
    ) {
        MeowConsoleMod.fakeOre().onExplosionBlocksChanged(this.level(), explodedPositions);
    }
}
