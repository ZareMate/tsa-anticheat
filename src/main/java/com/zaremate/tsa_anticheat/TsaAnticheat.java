package com.zaremate.tsa_anticheat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(TsaAnticheat.MOD_ID)
public final class TsaAnticheat {
    public static final String MOD_ID = "tsa_anticheat";

    public TsaAnticheat(IEventBus modEventBus) {
        modEventBus.addListener(TsaAnticheat::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(XrayReportPayload.TYPE, XrayReportPayload.STREAM_CODEC, XrayReportPayload::handle);
    }
}
