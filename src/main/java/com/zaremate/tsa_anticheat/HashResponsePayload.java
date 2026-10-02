package com.zaremate.tsa_anticheat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HashResponsePayload(
        String kind,
        String requestedName,
        String results,
        int failed
) implements CustomPacketPayload {
    public static final Type<HashResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    TsaAnticheat.MOD_ID,
                    "hash_response"
            ));

    public static final StreamCodec<ByteBuf, HashResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    HashResponsePayload::kind,
                    ByteBufCodecs.STRING_UTF8,
                    HashResponsePayload::requestedName,
                    ByteBufCodecs.STRING_UTF8,
                    HashResponsePayload::results,
                    ByteBufCodecs.VAR_INT,
                    HashResponsePayload::failed,
                    HashResponsePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HashResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                TsaHashManager.handleResponse(player, payload);
            }
        });
    }
}
