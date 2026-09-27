package com.zaremate.tsa_anticheat;

import com.zaremate.tsa_anticheat.client.PacketIntegrityClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TsaAnticheat.MOD_ID)
public final class TsaAnticheat {
    public static final String MOD_ID = "tsa_anticheat";
    public static final Logger LOGGER =
            LoggerFactory.getLogger(MOD_ID);

    public TsaAnticheat(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(
                ModConfig.Type.COMMON,
                TsaAnticheatConfig.SPEC
        );

        modEventBus.addListener(TsaAnticheat::registerPayloads);

        NeoForge.EVENT_BUS.register(PacketIntegrityCommands.class);
        NeoForge.EVENT_BUS.register(EventHandlers.class);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                XrayReportPayload.TYPE,
                XrayReportPayload.STREAM_CODEC,
                XrayReportPayload::handle
        );

        registrar.playToServer(
                PacketIntegrityResponsePayload.TYPE,
                PacketIntegrityResponsePayload.STREAM_CODEC,
                PacketIntegrityResponsePayload::handle
        );

        registrar.playToClient(
                PacketIntegrityChallengePayload.TYPE,
                PacketIntegrityChallengePayload.STREAM_CODEC,
                (payload, context) -> PacketIntegrityClient.handleChallenge(payload)
        );
    }
}
