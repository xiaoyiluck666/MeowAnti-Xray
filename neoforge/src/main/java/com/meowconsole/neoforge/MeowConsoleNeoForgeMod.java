package com.meowconsole.neoforge;

import com.meowconsole.MeowConsoleMod;
import com.meowconsole.MeowServerCommands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("meowantixray")
public final class MeowConsoleNeoForgeMod {
    public MeowConsoleNeoForgeMod(IEventBus modBus) {
        MeowConsoleMod.initialize(new NeoForgeModPlatform());
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerDisconnect);
        NeoForge.EVENT_BUS.addListener(this::onBlockBreak);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        MeowServerCommands.register(event.getDispatcher());
    }

    private void onServerStarted(ServerStartedEvent event) {
        MeowConsoleMod.onServerStarted(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        MeowConsoleMod.onServerStopping(event.getServer());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        MeowConsoleMod.onServerTick(event.getServer());
    }

    private void onPlayerJoin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            MeowConsoleMod.onPlayerJoin(player);
        }
    }

    private void onPlayerDisconnect(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            MeowConsoleMod.onPlayerDisconnect(player);
        }
    }

    private void onBlockBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player
            && event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            MeowConsoleMod.onPlayerBreakBlock(level, player, event.getPos());
        }
    }
}
