package com.zaremate.tsa_anticheat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandTargetsTest {
    @Test void busyTargetDoesNotPreventOtherTargetsFromStarting() {
        List<String> visited = new ArrayList<>();
        int started = CommandTargets.forEach(List.of("first", "busy", "last"), target -> {
            visited.add(target);
            return target.equals("busy") ? 0 : 1;
        });
        assertEquals(List.of("first", "busy", "last"), visited);
        assertEquals(2, started);
    }

    @Test void singlePlayerAndEmptyTargetsReturnAccurateCounts() {
        assertEquals(1, CommandTargets.forEach(List.of("player"), target -> 1));
        assertEquals(0, CommandTargets.forEach(List.of(), target -> {
            fail("No check should start without a target");
            return 1;
        }));
    }
}
