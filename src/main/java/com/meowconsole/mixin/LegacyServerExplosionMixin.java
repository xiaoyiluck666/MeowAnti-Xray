package com.meowconsole.mixin;

import com.meowconsole.MeowConsoleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "net.minecraft.world.level.Explosion")
abstract class LegacyServerExplosionMixin {
    @Shadow
    @Final
    private Level level;

    @Shadow
    public abstract boolean interactsWithBlocks();

    @Shadow
    public abstract List<BlockPos> getToBlow();

    @Inject(method = "finalizeExplosion", at = @At("RETURN"))
    private void meowconsole$refreshAntiXrayAfterLegacyExplosion(boolean spawnParticles, CallbackInfo ci) {
        if (!interactsWithBlocks() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        MeowConsoleMod.fakeOre().onExplosionBlocksChanged(serverLevel, getToBlow());
    }
}
