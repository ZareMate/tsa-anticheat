package com.zaremate.tsa_anticheat;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/** In-game reference for the registered /tsa command tree. */
final class TsaHelp {
    private TsaHelp() {}

    static int show(CommandSourceStack source, int page) {
        if (page < 1 || page > 3) {
            source.sendFailure(Component.literal("Help pages: 1-3. Use /tsa help <page>."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("=== TSA Anticheat Help (" + page + "/3) ===")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        for (String entry : TsaHelpPages.lines(page)) {
            String[] parts = entry.split("\\|", 2);
            var message = Component.literal(parts[0]).withStyle(ChatFormatting.YELLOW);
            if (parts.length > 1) {
                message = message.append(Component.literal(" - " + parts[1])
                        .withStyle(ChatFormatting.GRAY));
            }
            Component finalMessage = message;
            source.sendSuccess(() -> finalMessage, false);
        }
        source.sendSuccess(() -> Component.literal("Use /tsa help <page> for pages 1-3.")
                .withStyle(ChatFormatting.DARK_AQUA), false);
        return 1;
    }

}
