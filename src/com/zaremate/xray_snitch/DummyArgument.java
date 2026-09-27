package com.zaremate.xray_snitch;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.function.Supplier;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class DummyArgument implements ArgumentType<String> {
   public static final DeferredRegister<ArgumentTypeInfo<?, ?>> COMMAND_ARGUMENT_TYPES;
   public static final Supplier<ArgumentTypeInfo<?, ?>> DUMMY_ARGUMENT;

   static void register(IEventBus bus) {
      COMMAND_ARGUMENT_TYPES.register(bus);
   }

   public String parse(StringReader stringReader) throws CommandSyntaxException {
      return "Java ist scheiße";
   }

   static {
      COMMAND_ARGUMENT_TYPES = DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, "xray_snitch");
      DUMMY_ARGUMENT = COMMAND_ARGUMENT_TYPES.register("dummy", () -> ArgumentTypeInfos.registerByClass(DummyArgument.class, new Info()));
   }

   public static class Info implements ArgumentTypeInfo<DummyArgument, Template> {
      public void serializeToNetwork(Template template, FriendlyByteBuf friendlyByteBuf) {
      }

      public Template deserializeFromNetwork(FriendlyByteBuf friendlyByteBuf) {
         return new Template();
      }

      public void serializeToJson(Template template, JsonObject jsonObject) {
      }

      public Template unpack(DummyArgument info) {
         return new Template();
      }

      public class Template implements ArgumentTypeInfo.Template<DummyArgument> {
         public DummyArgument instantiate(CommandBuildContext commandBuildContext) {
            return new DummyArgument();
         }

         public ArgumentTypeInfo<DummyArgument, ?> type() {
            return Info.this;
         }
      }
   }
}
