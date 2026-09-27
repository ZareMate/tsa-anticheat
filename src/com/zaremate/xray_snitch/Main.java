package com.zaremate.xray_snitch;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod("xray_snitch")
public class Main {
   public static final String MODID = "xray_snitch";

   public Main(IEventBus modEventBus) {
      modEventBus.addListener(this::commonSetup);
      modEventBus.addListener(this::registerPayloads);
      modEventBus.addListener(this::onClientSetup);
      DummyArgument.register(modEventBus);
   }

   private void commonSetup(FMLCommonSetupEvent event) {
   }

   private void onClientSetup(FMLClientSetupEvent event) {
      NeoForge.EVENT_BUS.register(Event.class);
   }

   private void registerPayloads(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar registrar = event.registrar("1");
      registrar.playToServer(Packet.TYPE, Packet.STREAM_CODEC, Packet::handle);
   }
}
