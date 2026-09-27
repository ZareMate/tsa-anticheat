package com.zaremate.tsa_anticheat;

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

        registrar.playToServer(
                HashResponsePayload.TYPE,
                HashResponsePayload.STREAM_CODEC,
                HashResponsePayload::handle
        );

        registrar.playToClient(
                PacketIntegrityChallengePayload.TYPE,
                PacketIntegrityChallengePayload.STREAM_CODEC,
                (payload, context) -> handleClientChallenge(payload)
        );

        registrar.playToClient(
                HashRequestPayload.TYPE,
                HashRequestPayload.STREAM_CODEC,
                (payload, context) -> handleClientHashRequest(payload)
        );
    }

    private static void handleClientHashRequest(HashRequestPayload payload) {
        try {
            Class<?> handler = Class.forName(
                    "com.zaremate.tsa_anticheat.client.PacketIntegrityClient"
            );

            handler.getMethod(
                    "handleHashRequest",
                    HashRequestPayload.class
            ).invoke(null, payload);
        } catch (ClassNotFoundException ignored) {
            // Client-only handler is not present on a dedicated server.
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn(
                    "Failed to dispatch local hash request to the client handler.",
                    exception
            );
        }
    }

    private static void handleClientChallenge(PacketIntegrityChallengePayload payload) {
        try {
            Class<?> handler = Class.forName(
                    "com.zaremate.tsa_anticheat.client.PacketIntegrityClient"
            );

            handler.getMethod(
                    "handleChallenge",
                    PacketIntegrityChallengePayload.class
            ).invoke(null, payload);
        } catch (ClassNotFoundException ignored) {
            // Client-only handler is not present on a dedicated server.
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("Failed to dispatch packet integrity challenge to the client handler.", exception);
        }
    }
}
