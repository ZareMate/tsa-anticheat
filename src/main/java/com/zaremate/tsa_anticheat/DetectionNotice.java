package com.zaremate.tsa_anticheat;

import java.util.List;

final class DetectionNotice {
    private DetectionNotice() {}
    static String description(String player, String uuid, List<String> detections, String context, String action) {
        StringBuilder result = new StringBuilder("Player: **").append(escape(player)).append("**\nUUID: `")
                .append(escape(uuid)).append("`\nCheck: **").append(escape(context))
                .append("**\nStatus: **").append(detections.isEmpty() ? "CLEAN" : "DETECTED")
                .append("**\nAction taken: **").append(escape(action)).append("**\n\n");
        for (String detection : detections) result.append("- ").append(escape(detection)).append('\n');
        // Discord embed descriptions have a 4096-character limit. Keep the action before details.
        if (result.length() > 4000) return result.substring(0, 3980) + "\n… details truncated";
        return result.toString();
    }
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("*", "\\*").replace("_", "\\_").replace("`", "\\`");
    }
}
