package com.meowconsole.fabric;

import com.meowconsole.MeowConsoleMod;
import com.meowconsole.MeowServerCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class MeowConsoleFabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        MeowConsoleMod.initialize(new FabricModPlatform());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> MeowServerCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(MeowConsoleMod::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(MeowConsoleMod::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(MeowConsoleMod::onServerTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> MeowConsoleMod.onPlayerJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MeowConsoleMod.onPlayerDisconnect(handler.player));
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer && world instanceof ServerLevel serverLevel) {
                MeowConsoleMod.onPlayerBreakBlock(serverLevel, serverPlayer, pos);
            }
        });
    }
}
