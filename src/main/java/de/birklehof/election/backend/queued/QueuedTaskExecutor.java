package de.birklehof.election.backend.queued;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class QueuedTaskExecutor {

    private static final BlockingQueue<Runnable> TASK_QUEUE = new LinkedBlockingQueue<>();

    static {
        init();
    }

    private QueuedTaskExecutor() {
        throw new UnsupportedOperationException();
    }

    private static void init() {
        final var executingThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    TASK_QUEUE.take().run();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "TaskExecutor");
        executingThread.setDaemon(true);
        executingThread.start();
    }

    public static void queue(@NotNull Runnable runnable) {
        TASK_QUEUE.add(runnable);
    }
}
