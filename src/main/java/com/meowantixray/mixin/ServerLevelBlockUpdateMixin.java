package com.meowantixray.mixin;

import com.meowantixray.MeowAntiXrayMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.Objects;

@Mixin(Level.class)
abstract class ServerLevelBlockUpdateMixin {
    @Unique
    private static final ThreadLocal<ArrayDeque<BlockState>> meowconsole$oldStates = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void meowconsole$captureOldState(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        BlockPos safePos = Objects.requireNonNull(pos, "pos");
        if (((Object) this) instanceof ServerLevel serverLevel) {
            meowconsole$oldStates.get().push(serverLevel.getBlockState(safePos));
        }
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
    private void meowconsole$refreshAntiXrayNearby(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (!(((Object) this) instanceof ServerLevel serverLevel)) {
            return;
        }

        ArrayDeque<BlockState> stack = meowconsole$oldStates.get();
        BlockState oldState = stack.isEmpty() ? null : stack.pop();
        if (oldState == null || !cir.getReturnValue()) {
            return;
        }

        BlockPos safePos = Objects.requireNonNull(pos, "pos");
        MeowAntiXrayMod.fakeOre().onBlockChange(serverLevel, safePos, oldState, serverLevel.getBlockState(safePos));
    }
}
