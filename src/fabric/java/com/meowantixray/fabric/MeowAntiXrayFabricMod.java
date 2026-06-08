package com.meowantixray.fabric;

import com.meowantixray.MeowAntiXrayMod;
import com.meowantixray.MeowAntiXrayCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class MeowAntiXrayFabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        MeowAntiXrayMod.initialize(new FabricModPlatform());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> MeowAntiXrayCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(MeowAntiXrayMod::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(MeowAntiXrayMod::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(MeowAntiXrayMod::onServerTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> MeowAntiXrayMod.onPlayerJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MeowAntiXrayMod.onPlayerDisconnect(handler.player));
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer && world instanceof ServerLevel serverLevel) {
                MeowAntiXrayMod.onPlayerBreakBlock(serverLevel, serverPlayer, pos);
            }
        });
    }
}
