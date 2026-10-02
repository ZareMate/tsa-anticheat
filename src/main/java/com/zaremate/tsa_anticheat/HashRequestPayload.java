package com.zaremate.tsa_anticheat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record HashRequestPayload(String kind, String name) implements CustomPacketPayload {
    public static final Type<HashRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    TsaAnticheat.MOD_ID,
                    "hash_request"
            ));

    public static final StreamCodec<ByteBuf, HashRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    HashRequestPayload::kind,
                    ByteBufCodecs.STRING_UTF8,
                    HashRequestPayload::name,
                    HashRequestPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HashRequestPayload payload, IPayloadContext context) {
        // Client handling is registered by TsaAnticheat on the client.
    }
}
