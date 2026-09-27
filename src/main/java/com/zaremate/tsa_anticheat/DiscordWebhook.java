package com.zaremate.tsa_anticheat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;

public final class DiscordWebhook {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private DiscordWebhook() {
    }

    public static void send(String player, String uuid, String status, String details) {
        String url = TsaAnticheatConfig.webhookUrl();
        if (!TsaAnticheatConfig.WEBHOOK_ENABLED.get() || url.isBlank()) {
            return;
        }

        String description = "Player: **" + escapeMarkdown(player) + "**\n"
                + "UUID: `" + escapeMarkdown(uuid) + "`\n"
                + "Check: **PACKET INTEGRITY**\n"
                + "Status: **" + escapeMarkdown(status) + "**\n\n"
                + escapeMarkdown(details);

        String json = "{"
                + "\"username\":\"TSA Anticheat\","
                + "\"embeds\":[{"
                + "\"title\":\"TSA Anticheat result\","
                + "\"description\":\"" + escapeJson(description) + "\","
                + "\"color\":" + colorFor(status)
                + "}]"
                + "}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            TsaAnticheat.LOGGER.warn("Discord webhook returned HTTP {}", response.statusCode());
                        }
                    })
                    .exceptionally(error -> {
                        TsaAnticheat.LOGGER.warn("Discord webhook failed: {}", error.getMessage());
                        return null;
                    });
        } catch (Exception exception) {
            TsaAnticheat.LOGGER.warn("Invalid Discord webhook URL: {}", exception.getMessage());
        }
    }

    public static void sendResourcePackDetection(
            String player,
            String uuid,
            List<String> detections
    ) {
        String url = TsaAnticheatConfig.webhookUrl();

        if (!TsaAnticheatConfig.WEBHOOK_ENABLED.get()
                || url.isBlank()
                || detections == null
                || detections.isEmpty()) {
            return;
        }

        StringBuilder details = new StringBuilder();

        for (String detection : detections) {
            if (details.length() > 0) {
                details.append("\n");
            }

            details.append(escapeMarkdown(detection));
        }

        String description = "Player: **" + escapeMarkdown(player) + "**\n"
                + "UUID: " + escapeMarkdown(uuid) + "\n"
                + "Check: **BLACKLISTED RESOURCE PACK**\n"
                + "Status: **DETECTED**\n\n"
                + details;

        String json = "{"
                + "\"username\":\"TSA Anticheat\","
                + "\"embeds\":[{"
                + "\"title\":\"TSA Anticheat detection\","
                + "\"description\":\"" + escapeJson(description) + "\","
                + "\"color\":" + colorFor("DETECTED")
                + "}]"
                + "}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        if (response.statusCode() < 200
                                || response.statusCode() >= 300) {
                            TsaAnticheat.LOGGER.warn(
                                    "Discord webhook returned HTTP {}",
                                    response.statusCode()
                            );
                        }
                    })
                    .exceptionally(error -> {
                        TsaAnticheat.LOGGER.warn(
                                "Discord webhook failed: {}",
                                error.getMessage()
                        );
                        return null;
                    });
        } catch (Exception exception) {
            TsaAnticheat.LOGGER.warn(
                    "Invalid Discord webhook URL: {}",
                    exception.getMessage()
            );
        }
    }

    private static int colorFor(String status) {
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PASS" -> 3066993;
            case "MODIFIED" -> 15158332;
            default -> 16776960;
        };
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String escapeMarkdown(String value) {
        return value.replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`");
    }
}
