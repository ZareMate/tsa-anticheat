package com.zaremate.tsa_anticheat;

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
                        .then(Commands.literal("hash")
                                .then(Commands.literal("mod")
                                        .then(Commands.literal("*")
                                                .executes(context -> startHash(
                                                        context.getSource(),
                                                        "MOD",
                                                        "*"
                                                )))
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> startHash(
                                                        context.getSource(),
                                                        "MOD",
                                                        StringArgumentType.getString(context, "name")
                                                )))
                                )
                                .then(Commands.literal("resourcepack")
                                        .then(Commands.literal("*")
                                                .executes(context -> startHash(
                                                        context.getSource(),
                                                        "RESOURCE_PACK",
                                                        "*"
                                                )))
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> startHash(
                                                        context.getSource(),
                                                        "RESOURCE_PACK",
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
                        ("*".equals(name) ? " hash scan." : " hash for " + name + ".")
                ),
                false
        );

        return 1;
    }

    private static void saveGeneratedHash(
            String type,
            String name,
            String hash
    ) throws java.io.IOException {
        Path directory =
                FMLPaths.CONFIGDIR.get().resolve(TsaAnticheat.MOD_ID);
        Path file = directory.resolve("generated_hashes.txt");

        Files.createDirectories(directory);

        if (Files.notExists(file)) {
            Files.writeString(
                    file,
                    "# TSA Anticheat generated hashes\n"
                            + "# Generated by /tsa hash\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW
            );
        }

        String line = type + " | " + name + " | " + hash;

        for (String existing : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (existing.equals(line)) {
                return;
            }
        }

        Files.writeString(
                file,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private static CompletableFuture<Suggestions> suggestEntries(
            SuggestionsBuilder builder,
            Path directory
    ) {
        try (var stream = Files.list(directory)) {
            var suggestions = new java.util.ArrayList<String>();
            suggestions.add("*");
            suggestions.addAll(
                    stream
                            .map(path -> path.getFileName().toString())
                            .sorted()
                            .toList()
            );

            return SharedSuggestionProvider.suggest(suggestions, builder);
        } catch (Exception exception) {
            return builder.buildFuture();
        }
    }
}
