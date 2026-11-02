package com.zaremate.tsa_anticheat;

import java.util.ArrayList;
import java.util.List;

/** Discord payloads for informational inventory reports, including empty and incomplete reports. */
final class UnlistedNotice {
    private static final int PAGE_SIZE = 3500;
    private UnlistedNotice() {}

    static List<String> payloads(String report) {
        String escaped = report.replace("\\", "\\\\").replace("*", "\\*")
                .replace("_", "\\_").replace("`", "\\`");
        List<String> pages = new ArrayList<>();
        while (escaped.length() > PAGE_SIZE) {
            int end = escaped.lastIndexOf('\n', PAGE_SIZE);
            if (end <= 0) end = PAGE_SIZE;
            if (Character.isHighSurrogate(escaped.charAt(end - 1))) end--;
            pages.add(escaped.substring(0, end));
            escaped = escaped.substring(end);
        }
        pages.add(escaped);
        List<String> payloads = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            String description = "Check: **UNLISTED INVENTORY**\nStatus: **INFORMATIONAL**\n"
                    + "Action taken: **None — inventory only, not evidence of cheating.**\n"
                    + "Page " + (i + 1) + "/" + pages.size() + "\n\n" + pages.get(i);
            payloads.add("{\"username\":\"TSA Anticheat\",\"allowed_mentions\":{\"parse\":[]},\"embeds\":[{"
                    + "\"title\":\"TSA Anticheat unlisted inventory\",\"description\":"
                    + jsonString(description) + ",\"color\":3447003}]}");
        }
        return List.copyOf(payloads);
    }

    private static String jsonString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) out.append(String.format("\\u%04x", (int) character));
                    else out.append(character);
                }
            }
        }
        return out.append('"').toString();
    }
}
