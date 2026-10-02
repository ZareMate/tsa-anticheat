package com.zaremate.tsa_anticheat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record PacketIntegrityResponsePayload(String challenge, String response) implements CustomPacketPayload {
    public static final Type<PacketIntegrityResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TsaAnticheat.MOD_ID, "packet_response"));

    public static final StreamCodec<ByteBuf, PacketIntegrityResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    PacketIntegrityResponsePayload::challenge,
                    ByteBufCodecs.STRING_UTF8,
                    PacketIntegrityResponsePayload::response,
                    PacketIntegrityResponsePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketIntegrityResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketIntegrityManager.handleResponse(player, payload);
            }
        });
    }
}
