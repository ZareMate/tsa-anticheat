package com.zaremate.tsa_anticheat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TsaWorkerTest {
    @TempDir Path directory;

    @BeforeEach void start() { TsaWorker.start(); }
    @AfterEach void stop() { TsaWorker.stop(); }

    @Test void slowWorkDoesNotBlockCallerAndJobsRunInOrder() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Integer> order = new ArrayList<>();
        Thread caller = Thread.currentThread();
        try {
            var first = TsaWorker.submit(() -> {
                assertNotSame(caller, Thread.currentThread());
                entered.countDown();
                await(release);
                order.add(1);
                return null;
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = TsaWorker.submit(() -> { order.add(2); return null; });
            assertFalse(first.isDone());
            assertFalse(second.isDone());
            release.countDown();
            second.get(5, TimeUnit.SECONDS);
            assertEquals(List.of(1, 2), order);
        } finally {
            release.countDown();
        }
    }

    @Test void failedTaskDoesNotStopLaterWork() throws Exception {
        var failed = TsaWorker.submit(() -> { throw new IllegalStateException("test"); });
        assertThrows(java.util.concurrent.ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS));
        assertEquals(42, TsaWorker.submit(() -> 42).get(5, TimeUnit.SECONDS));
    }

    @Test void overloadedQueueRejectsWithoutRunningOnCaller() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            TsaWorker.submit(() -> { entered.countDown(); await(release); return null; });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            boolean rejected = false;
            for (int i = 0; i < 2000; i++) {
                var future = TsaWorker.submit(() -> {
                    assertEquals("tsa-anticheat-worker", Thread.currentThread().getName());
                    return null;
                });
                if (future.isCompletedExceptionally()) {
                    rejected = true;
                    break;
                }
            }
            assertTrue(rejected, "Queue must have a finite capacity");
        } finally {
            release.countDown();
        }
    }

    @Test void shutdownDrainsWritesRejectsNewWorkAndSupportsRestart() throws Exception {
        Path file = directory.resolve("saved.txt");
        CompletableFuture<Void> write = TsaWorker.submit(() -> {
            try { Files.writeString(file, "saved"); }
            catch (Exception error) { throw new RuntimeException(error); }
            return null;
        });
        TsaWorker.stop();
        assertTrue(write.isDone());
        assertEquals("saved", Files.readString(file));
        assertTrue(TsaWorker.submit(() -> 1).isCompletedExceptionally());
        TsaWorker.start();
        assertEquals(2, TsaWorker.submit(() -> 2).get(5, TimeUnit.SECONDS));
    }

    @Test void reportRemovalAndSubsequentAppendDoNotLoseUpdates() throws Exception {
        String hash = "a".repeat(64);
        String detection = "2026-10-28 | DETECTED | MOD Xray.jar [" + hash + "]\n";
        Path report = directory.resolve(UUID.randomUUID() + ".txt");
        var written = TsaWorker.submit(() -> {
            try { Files.writeString(report, detection); }
            catch (Exception error) { throw new RuntimeException(error); }
            return null;
        });
        var removed = TsaWorker.submit(() -> {
            try { return DetectionRemoval.removeFrom(directory, "hash", hash); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        var appended = TsaWorker.submit(() -> {
            try { Files.writeString(report, "packet result\n", java.nio.file.StandardOpenOption.APPEND); }
            catch (Exception error) { throw new RuntimeException(error); }
            return null;
        });
        appended.get(5, TimeUnit.SECONDS);
        written.get(5, TimeUnit.SECONDS);
        assertEquals(1, removed.get(5, TimeUnit.SECONDS).detections());
        assertEquals("packet result\n", Files.readString(report));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }
}
