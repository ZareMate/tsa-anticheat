package com.zaremate.tsa_anticheat.client;

import com.zaremate.tsa_anticheat.PacketIntegrityChallengePayload;
import com.zaremate.tsa_anticheat.PacketIntegrityManager;
import com.zaremate.tsa_anticheat.PacketIntegrityResponsePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PacketIntegrityClient {
    private PacketIntegrityClient() {}

    public static void handleChallenge(PacketIntegrityChallengePayload payload) {
        var minecraft = Minecraft.getInstance();
        var gameProfile = minecraft.getGameProfile();

        if (gameProfile == null || minecraft.player == null) {
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
