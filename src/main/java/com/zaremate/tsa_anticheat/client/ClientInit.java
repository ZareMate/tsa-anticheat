package com.zaremate.tsa_anticheat.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "tsa_anticheat", dist = Dist.CLIENT)
public final class ClientInit {
    public ClientInit(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(ClientEvents.class);
    }
}
