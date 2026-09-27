package com.zaremate.xray_snitch;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class Event {
   @SubscribeEvent
   public static void onJoinServer(ClientPlayerNetworkEvent.LoggingIn event) {
      String resourcePacks = System.getProperty("user.dir") + "/resourcepacks";
      File[] files = (new File(resourcePacks)).listFiles();
      List<String> results = new ArrayList();
      if (files != null) {
         for(File file : files) {
            String fileName = file.getName();
            if (fileName.toLowerCase().contains("ray")) {
               results.add(file.getName());
            }
         }
      }

      if (!results.isEmpty()) {
         PacketDistributor.sendToServer(new Packet(String.join(", ", results)), new CustomPacketPayload[0]);
      }

   }
}
