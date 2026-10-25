package cn.threatexpert.gonc;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class ReceiveWorkerPool {
    static final int MAX_WORKERS = 4;
    interface Task { void run(int index) throws Exception; }

    static void run(int count, int requestedWorkers, HttpReceiver.Session session, Task task) throws Exception {
        if (count == 0 || session.isStopped()) return;
        int workers = Math.min(count, Math.max(1, Math.min(MAX_WORKERS, requestedWorkers)));
        AtomicInteger next = new AtomicInteger();
        AtomicInteger threadId = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(workers,
                action -> new Thread(action, "gonc-download-" + threadId.incrementAndGet()));
        session.attachExecutor(pool);
        boolean interrupted = false;
        try {
            // Only queue worker loops, not hundreds of per-file tasks.
            for (int i = 0; i < workers; i++) {
                pool.execute(() -> {
                    while (!session.isStopped() && failure.get() == null) {
                        int index = next.getAndIncrement();
                        if (index >= count || session.isStopped()) return;
                        try {
                            task.run(index);
                        } catch (Throwable error) {
                            failure.compareAndSet(null, error);
                            return;
                        }
                    }
                });
            }
        } catch (RejectedExecutionException error) {
            if (!session.isStopped()) failure.compareAndSet(null, error);
        } finally {
            pool.shutdown();
            // Termination is reported only after every worker has closed its resources.
            while (!pool.isTerminated()) {
                try {
                    pool.awaitTermination(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException error) {
                    interrupted = true;
                    session.stop();
                }
            }
            session.detachExecutor(pool);
            if (interrupted) Thread.currentThread().interrupt();
        }
        Throwable error = failure.get();
        if (error instanceof Exception) throw (Exception) error;
        if (error != null) throw new RuntimeException(error);
    }
}
