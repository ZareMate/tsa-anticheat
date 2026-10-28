package com.zaremate.tsa_anticheat;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import java.util.UUID;

final class EnforcementCommands {
    private EnforcementCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        var root = Commands.literal("enforcement")
                .executes(ctx -> run(ctx.getSource(), EnforcementCommands::status))
                .then(Commands.literal("status").executes(ctx -> run(ctx.getSource(), EnforcementCommands::status)));
        var mode = Commands.literal("mode");
        for (String value : new String[]{"off", "kick", "tempban"}) {
            mode.then(Commands.literal(value).executes(ctx -> run(ctx.getSource(), () -> setMode(value))));
        }
        root.then(mode);
        for (String action : new String[]{"kick", "tempban"}) {
            root.then(Commands.literal(action).then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(ctx -> {
                        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                        return run(ctx.getSource(), () -> {
                            if (enabled) return setMode(action);
                            if (TsaAnticheatConfig.ENFORCEMENT_MODE.get().equals(action)) return setMode("off");
                            return action + " is already disabled. " + status();
                        });
                    })));
        }
        root.then(Commands.literal("duration").then(Commands.argument("duration", StringArgumentType.word())
                .suggests((ctx, builder) -> { for (String duration : new String[]{"30m", "1h", "1d", "7d"}) builder.suggest(duration); return builder.buildFuture(); })
                .executes(ctx -> {
                    String duration = StringArgumentType.getString(ctx, "duration");
                    return run(ctx.getSource(), () -> {
                        int seconds = EnforcementPolicy.seconds(duration);
                        TsaAnticheatConfig.TEMPBAN_SECONDS.set(seconds); TsaAnticheatConfig.TEMPBAN_SECONDS.save();
                        return "New tempbans last " + seconds + " seconds. Existing bans are unchanged.";
                    });
                })));
        var hash = Commands.literal("hash");
        hash.then(Commands.literal("list").executes(ctx -> run(ctx.getSource(), () -> {
            var hashes = HashEnforcement.store().hashes();
            return hashes.isEmpty() ? "Enforcement hash list is empty." : "Enforcement hashes (" + hashes.size() + "):\n" + String.join("\n", hashes);
        })));
        for (String operation : new String[]{"add", "remove"}) {
            hash.then(Commands.literal(operation).then(Commands.argument("hash", StringArgumentType.word())
                    .executes(ctx -> {
                        String value = StringArgumentType.getString(ctx, "hash");
                        return run(ctx.getSource(), () -> {
                            boolean add = operation.equals("add");
                            boolean changed = HashEnforcement.store().editHash(value, add);
                            String result = changed ? (add ? "Added enforcement hash." : "Removed enforcement hash.")
                                    : (add ? "Hash is already listed." : "Hash was not listed.");
                            if (add && TsaFilenameDetection.isHashAllowed(value)) result += " This hash is allowlisted; it will not trigger enforcement.";
                            return result;
                        });
                    })));
        }
        root.then(hash);
        root.then(Commands.literal("unban").then(Commands.argument("uuid", StringArgumentType.word())
                .executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "uuid");
                    return run(ctx.getSource(), () -> HashEnforcement.store().unban(UUID.fromString(id))
                            ? "Removed TSA tempban for " + id + "." : "No TSA tempban stored for " + id + ".");
                })));
        return root;
    }

    private static String status() throws java.io.IOException {
        return "Hash enforcement: " + TsaAnticheatConfig.ENFORCEMENT_MODE.get() + "; tempban duration: "
                + TsaAnticheatConfig.TEMPBAN_SECONDS.get() + " seconds; manual hashes: " + HashEnforcement.store().hashes().size() + ".";
    }
    private static String setMode(String value) {
        TsaAnticheatConfig.ENFORCEMENT_MODE.set(value); TsaAnticheatConfig.ENFORCEMENT_MODE.save();
        return "Hash enforcement mode: " + value + ". Existing tempbans retain their expiry.";
    }
    private interface Action { String run() throws Exception; }
    private static int run(CommandSourceStack source, Action action) {
        var server = source.getServer();
        TsaWorker.submit(() -> {
            try { return action.run(); } catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
        }).whenComplete((message, error) -> server.execute(() -> {
            if (!server.isRunning()) return;
            if (error == null) source.sendSuccess(() -> Component.literal(message), false);
            else {
                Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
                source.sendFailure(Component.literal("Enforcement command failed: " + cause.getMessage()));
            }
        }));
        return 1;
    }
}
