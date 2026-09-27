package com.zaremate.tsa_anticheat.client;

import com.zaremate.tsa_anticheat.PacketIntegrityChallengePayload;
import com.zaremate.tsa_anticheat.PacketIntegrityManager;
import com.zaremate.tsa_anticheat.PacketIntegrityResponsePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PacketIntegrityClient {
    private PacketIntegrityClient() {}

    public static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(
                PacketIntegrityChallengePayload.TYPE,
                (payload, context) -> handleChallenge(payload)
        );
    }

    private static void handleChallenge(PacketIntegrityChallengePayload payload) {
        var gameProfile = Minecraft.getInstance().getGameProfile();

        if (gameProfile == null || Minecraft.getInstance().player == null) {
            return;
        }

        String response = PacketIntegrityManager.createResponse(
                gameProfile.getId(),
                payload.challenge()
        );

        PacketDistributor.sendToServer(
                new PacketIntegrityResponsePayload(
                        payload.challenge(),
                        response
                )
        );
    }
}
