package com.zaremate.tsa_anticheat;

import java.util.*;
import java.util.regex.Pattern;

final class EnforcementPolicy {
    private static final Pattern DURATION = Pattern.compile("([1-9][0-9]*)([smhd]?)", Pattern.CASE_INSENSITIVE);
    private EnforcementPolicy() {}

    static String hash(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Expected exactly 64 hexadecimal SHA-256 characters.");
        return normalized;
    }

    static int seconds(String value) {
        var match = DURATION.matcher(value);
        if (!match.matches()) throw new IllegalArgumentException("Use a positive duration such as 30m, 1h, 7d, or seconds.");
        try {
            long multiplier = switch (match.group(2).toLowerCase(Locale.ROOT)) {
                case "m" -> 60; case "h" -> 3600; case "d" -> 86400; default -> 1;
            };
            long seconds = Math.multiplyExact(Long.parseLong(match.group(1)), multiplier);
            if (seconds > 31_536_000) throw new ArithmeticException();
            return (int) seconds;
        } catch (ArithmeticException | NumberFormatException error) {
            throw new IllegalArgumentException("Tempban duration must be between 1 second and 365 days.");
        }
    }

    static List<String> matchingFiles(String report, Set<String> enforcement, Set<String> allowed) {
        Set<String> names = new LinkedHashSet<>();
        for (String line : report.split("\\R")) {
            int first = line.indexOf('\t'), last = line.lastIndexOf('\t');
            if (first <= 0 || last <= first || last == line.length() - 1) continue;
            String type = line.substring(0, first).trim().toUpperCase(Locale.ROOT);
            if (!(type.equals("MOD") || type.equals("RESOURCE_PACK"))) continue;
            String hash = line.substring(last + 1).trim().toLowerCase(Locale.ROOT);
            if (hash.matches("[0-9a-f]{64}") && enforcement.contains(hash) && !allowed.contains(hash)) {
                names.add(filename(line.substring(first + 1, last)));
            }
        }
        return List.copyOf(names);
    }

    static String action(String configured, List<String> files) {
        if (!Set.of("off", "kick", "tempban").contains(configured)) throw new IllegalArgumentException("Unknown enforcement mode");
        return files.isEmpty() ? "off" : configured;
    }

    static String filename(String value) {
        String name = value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}§]", "").strip();
        // Do not let a client-supplied filename masquerade as a hash in the disconnect reason.
        name = name.replaceAll("(?i)[0-9a-f]{64}", "[file]");
        return name.isEmpty() ? "unnamed file" : name.substring(0, Math.min(name.length(), 160));
    }

    static String message(String mode, List<String> files, long until) {
        String names = String.join(", ", files.stream().limit(8).toList());
        if (files.size() > 8) names += " and " + (files.size() - 8) + " more";
        return "[TSA Anticheat] " + (mode.equals("tempban")
                ? "Temporarily banned until " + java.time.Instant.ofEpochMilli(until) : "Kicked")
                + ". Prohibited file(s): " + names;
    }
}
