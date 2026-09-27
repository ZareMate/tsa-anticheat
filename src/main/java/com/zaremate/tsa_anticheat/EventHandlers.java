package com.zaremate.tsa_anticheat;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class EventHandlers {
    private EventHandlers() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PacketIntegrityManager.expireChecks();
    }
}
