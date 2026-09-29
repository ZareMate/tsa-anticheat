package com.zaremate.tsa_anticheat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
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
                        .requires(TsaPermissions::canUseCommands)
                        .then(Commands.literal("packetcheck")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> startCheck(
                                                context.getSource(),
                                                EntityArgument.getPlayer(
                                                        context,
                                                        "player"
                                                )
                                        )))
                        )
                        .then(Commands.literal("checkpacket")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> startCheck(
                                                context.getSource(),
                                                EntityArgument.getPlayer(
                                                        context,
                                                        "player"
                                                )
                                        )))
                        )
                        .then(Commands.literal("detect")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> startDetection(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")
                                        )))
                        )
                        .then(Commands.literal("hash")
                                .then(Commands.literal("mod")
                                        .then(Commands.literal("*")
                                                .executes(context -> startHash(
                                                        context.getSource(),
                                                        "MOD",
                                                        "*"
                                                )))
                                        .then(Commands.argument(
                                                "name",
                                                StringArgumentType.string()
                                        )
                                                .executes(context -> startHash(
                                                        context.getSource(),
                                                        "MOD",
                                                        StringArgumentType.getString(
                                                                context,
                                                                "name"
                                                        )
                                                )))
                                )
                                .then(Commands.literal("resourcepack")
                                        .then(Commands.literal("*")
                                                .executes(context -> startHash(
                                                        context.getSource(),
                                                        "RESOURCE_PACK",
                                                        "*"
                                                )))
                                        .then(Commands.argument(
                                                "name",
                                                StringArgumentType.string()
                                        )
                                                .executes(context -> startHash(
                                                        context.getSource(),
                                                        "RESOURCE_PACK",
                                                        StringArgumentType.getString(
                                                                context,
                                                                "name"
                                                        )
                                                )))
                                )
                        )
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

    private static int startDetection(CommandSourceStack source, ServerPlayer target) {
        UUID requester = source.getEntity() instanceof ServerPlayer player
                ? player.getUUID()
                : null;

        if (!TsaHashManager.startDetection(target, requester)) {
            source.sendFailure(
                    Component.literal(
                            "A detection scan is already pending for " +
                            target.getGameProfile().getName() + "."
                    )
            );
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Started detection scan for " +
                        target.getGameProfile().getName()
                ),
                false
        );

        return 1;
    }

    private static int startHash(
            CommandSourceStack source,
            String type,
            String name
    ) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(
                    Component.literal(
                            "This command must be run by a player with TSA Anticheat installed."
                    )
            );
            return 0;
        }

        TsaHashManager.start(player, type, name);

        source.sendSuccess(
                () -> Component.literal(
                        "Requested local " + type.toLowerCase() +
                        ("*".equals(name)
                                ? " hash scan."
                                : " hash for " + name + ".")
                ),
                false
        );

        return 1;
    }
}
