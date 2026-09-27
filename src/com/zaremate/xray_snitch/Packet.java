package com.zaremate.xray_snitch;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record Packet(String data) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<Packet> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("xray_snitch", "packet"));
   public static final StreamCodec<FriendlyByteBuf, Packet> STREAM_CODEC;

   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   private static void writeMessageToFile(ServerPlayer player, String message) {
      Path configPath = FMLPaths.CONFIGDIR.get();
      Path subDirPath = configPath.resolve("xray_snitch");
      File subDir = subDirPath.toFile();
      if (!subDir.exists()) {
         subDir.mkdirs();
      }

      File file = subDirPath.resolve(String.valueOf(player.getUUID()) + ".txt").toFile();
      LocalDate currentDate = LocalDate.now();

      try {
         if (file.exists()) {
            List var10000 = Files.readAllLines(file.toPath());
            String var10001 = String.valueOf(currentDate);
            if (var10000.contains(var10001 + " | " + message)) {
               return;
            }
         }

         Path var9 = file.toPath();
         String var10 = String.valueOf(currentDate);
         Files.write(var9, (var10 + " | " + message + System.lineSeparator()).getBytes(), new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND});
      } catch (IOException e) {
         e.printStackTrace();
      }

   }

   public static void handle(Packet msg, IPayloadContext ctx) {
      ctx.enqueueWork(() -> {
         Player patt0$temp = ctx.player();
         if (patt0$temp instanceof ServerPlayer player) {
            writeMessageToFile(player, msg.data());
         }

      });
   }

   static {
      STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Packet::data, Packet::new);
   }
}
