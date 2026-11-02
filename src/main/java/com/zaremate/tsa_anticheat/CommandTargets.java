package com.zaremate.tsa_anticheat;

import java.util.Collection;
import java.util.function.ToIntFunction;

final class CommandTargets {
    private CommandTargets() {}

    static <T> int forEach(Collection<T> targets, ToIntFunction<T> action) {
        int started = 0;
        for (T target : targets) started += action.applyAsInt(target);
        return started;
    }
}
