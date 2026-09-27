package com.zaremate.tsa_anticheat;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

public final class PacketIntegrityCommands {
    private PacketIntegrityCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("tsa")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("packetcheck")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> startCheck(context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("checkpacket")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> startCheck(context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))
        );
    }

    private static int startCheck(CommandSourceStack source, ServerPlayer target) {
        UUID requester = source.getEntity() instanceof ServerPlayer player
                ? player.getUUID()
                : null;

        PacketIntegrityManager.start(target, requester);

        source.sendSuccess(
                () -> Component.literal(
                        "Started packet integrity check for " +
                        target.getGameProfile().getName()
                ),
                true
        );

        return 1;
    }
}
