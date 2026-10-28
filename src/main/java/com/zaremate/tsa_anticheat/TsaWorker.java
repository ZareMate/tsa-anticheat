package com.zaremate.tsa_anticheat;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Ordered, bounded background work. Never falls back to running on the caller. */
public final class TsaWorker {
    private static ThreadPoolExecutor executor;

    private TsaWorker() {}

    static synchronized void start() {
        if (executor != null) throw new IllegalStateException("TSA worker already started");
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1024), runnable -> {
                    Thread thread = new Thread(runnable, "tsa-anticheat-worker");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public static synchronized <T> CompletableFuture<T> submit(Supplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (executor == null) {
            result.completeExceptionally(new RejectedExecutionException("TSA worker is stopped"));
            return result;
        }
        try {
            executor.execute(() -> {
                try {
                    result.complete(task.get());
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            });
        } catch (RejectedExecutionException error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    static void execute(Runnable task) {
        submit(() -> { task.run(); return null; }).exceptionally(error -> {
            TsaAnticheat.LOGGER.error("TSA background task failed", error);
            return null;
        });
    }

    // Called only at server shutdown, never during a tick. Drain accepted writes
    // before another integrated-server session can use the same files.
    static void stop() {
        ThreadPoolExecutor stopping;
        synchronized (TsaWorker.class) {
            stopping = executor;
        }
        if (stopping == null) return;
        stopping.shutdown();
        boolean interrupted = false;
        while (!stopping.isTerminated()) {
            try {
                stopping.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                interrupted = true;
            }
        }
        synchronized (TsaWorker.class) {
            executor = null;
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
