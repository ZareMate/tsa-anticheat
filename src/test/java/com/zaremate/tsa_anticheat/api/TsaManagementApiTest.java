package com.zaremate.tsa_anticheat.api;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class TsaManagementApiTest {
    @Test void invalidSettingsReturnFailedFuturesWithoutNeedingARunningServer() {
        var invalid = TsaAnticheatAPI.setSettingAsync(TsaSetting.ENFORCEMENT_MODE, "ban");
        assertTrue(invalid.isCompletedExceptionally());
        assertInstanceOf(IllegalArgumentException.class,
                assertThrows(CompletionException.class, invalid::join).getCause());
        assertTrue(TsaAnticheatAPI.setSettingAsync(null, true).isCompletedExceptionally());
    }

    @Test void stoppedWorkerRejectsManagementWithoutExecutingFileOperationsOnCaller() {
        var result = TsaAnticheatAPI.addHashAsync(HashList.ENFORCEMENT, "a".repeat(64));
        assertTrue(result.isCompletedExceptionally());
        assertInstanceOf(RejectedExecutionException.class,
                assertThrows(CompletionException.class, result::join).getCause());
    }
}
