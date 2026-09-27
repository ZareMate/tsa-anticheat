package com.zaremate.tsa_anticheat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

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
                                        .executes(context -> startCheck(
                                                context.getSource(),
                                                net.minecraft.commands.arguments.EntityArgument.getPlayer(
                                                        context,
                                                        "player"
                                                )
                                        )))
                        )
                        .then(Commands.literal("checkpacket")
                                .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(context -> startCheck(
                                                context.getSource(),
                                                net.minecraft.commands.arguments.EntityArgument.getPlayer(
                                                        context,
                                                        "player"
                                                )
                                        )))
                        )
                        .then(Commands.literal("hash")
                                .then(Commands.literal("mod")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .suggests((context, builder) ->
                                                        suggestEntries(builder, FMLPaths.GAMEDIR.get().resolve("mods")))
                                                .executes(context -> hashEntry(
                                                        context.getSource(),
                                                        "MOD",
                                                        FMLPaths.GAMEDIR.get().resolve("mods"),
                                                        StringArgumentType.getString(context, "name")
                                                )))
                                )
                                .then(Commands.literal("resourcepack")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .suggests((context, builder) ->
                                                        suggestEntries(
                                                                builder,
                                                                FMLPaths.GAMEDIR.get().resolve("resourcepacks")
                                                        ))
                                                .executes(context -> hashEntry(
                                                        context.getSource(),
                                                        "RESOURCE_PACK",
                                                        FMLPaths.GAMEDIR.get().resolve("resourcepacks"),
                                                        StringArgumentType.getString(context, "name")
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

    private static int hashEntry(
            CommandSourceStack source,
            String type,
            Path directory,
            String name
    ) {
        Path entry = directory.resolve(name);

        if (!entry.normalize().startsWith(directory.normalize())
                || !Files.exists(entry)) {
            source.sendFailure(
                    Component.literal(
                            "Could not find " + type.toLowerCase() + ": " + name
                    )
            );
            return 0;
        }

        try {
            String hash = com.zaremate.tsa_anticheat.client.ResourcePackHasher.hash(entry);

            source.sendSuccess(
                    () -> Component.literal(
                            type + " " + name + "\nSHA-256: " + hash
                    ),
                    false
            );

            return 1;
        } catch (Exception exception) {
            TsaAnticheat.LOGGER.warn(
                    "Failed to hash {} {}",
                    type,
                    entry,
                    exception
            );

            source.sendFailure(
                    Component.literal(
                            "Failed to hash " + type.toLowerCase() + " '" + name + "'."
                    )
            );
            return 0;
        }
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestEntries(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder,
            Path directory
    ) {
        try (Stream<Path> stream = Files.list(directory)) {
            return SharedSuggestionProvider.suggest(
                    stream
                            .map(path -> path.getFileName().toString())
                            .sorted()
                            .toList(),
                    builder
            );
        } catch (Exception exception) {
            return builder.buildFuture();
        }
    }
}
