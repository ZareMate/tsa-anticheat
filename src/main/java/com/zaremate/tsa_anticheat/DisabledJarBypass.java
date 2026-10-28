package com.zaremate.tsa_anticheat;

import java.util.Locale;

/** Server-side filter for disabled mod JARs; never exempts enabled JARs or resource packs. */
final class DisabledJarBypass {
    private DisabledJarBypass() {}

    static boolean isDisabledJar(String type, String name) {
        return "MOD".equalsIgnoreCase(type)
                && name.toLowerCase(Locale.ROOT).endsWith(".jar.disabled");
    }

    static boolean ignore(String type, String name) {
        return ignore(type, name, TsaAnticheatConfig.BYPASS_DISABLED_JARS.get());
    }

    static boolean ignore(String type, String name, boolean enabled) {
        return enabled && isDisabledJar(type, name);
    }

    static String filterReport(String report) {
        return filterReport(report, TsaAnticheatConfig.BYPASS_DISABLED_JARS.get());
    }

    static String filterReport(String report, boolean enabled) {
        if (!enabled) return report;
        StringBuilder result = new StringBuilder();
        for (String line : report.split("\\R")) {
            int first = line.indexOf('\t');
            int last = line.lastIndexOf('\t');
            if (first > 0 && last > first
                    && isDisabledJar(line.substring(0, first), line.substring(first + 1, last))) {
                continue;
            }
            if (!result.isEmpty()) result.append('\n');
            result.append(line);
        }
        return result.toString();
    }
}
