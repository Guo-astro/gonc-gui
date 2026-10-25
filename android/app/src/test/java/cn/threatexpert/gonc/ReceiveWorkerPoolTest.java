package cn.threatexpert.gonc;

import org.junit.Test;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ReceiveWorkerPoolTest {
    @Test public void supportsOneTwoAndFourWorkersAndCapsLargerRequests() throws Exception {
        for (int requested : new int[]{1, 2, 4, 20}) {
            int expected = Math.min(4, requested);
            HttpReceiver.Session session = new HttpReceiver.Session();
            CountDownLatch wave = new CountDownLatch(expected);
            AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger(), completed = new AtomicInteger();
            ReceiveWorkerPool.run(40, requested, session, index -> {
                int current = active.incrementAndGet();
                peak.accumulateAndGet(current, Math::max);
                wave.countDown();
                try {
                    if (!wave.await(5, TimeUnit.SECONDS)) throw new IOException("workers did not overlap");
                    completed.incrementAndGet();
                } finally { active.decrementAndGet(); }
            });
            assertEquals(expected, peak.get());
            assertEquals(40, completed.get());
            assertEquals(0, active.get());
        }
    }

    @Test public void stoppedSessionNeverStartsJobs() throws Exception {
        HttpReceiver.Session session = new HttpReceiver.Session();
        session.stop();
        AtomicInteger started = new AtomicInteger();
        ReceiveWorkerPool.run(400, 4, session, index -> started.incrementAndGet());
        assertEquals(0, started.get());
    }

    @Test public void cancellationWaitsForAllWorkerCleanupBeforeTermination() throws Exception {
        HttpReceiver.Session session = new HttpReceiver.Session();
        CountDownLatch entered = new CountDownLatch(4), cleaning = new CountDownLatch(4), release = new CountDownLatch(1);
        AtomicInteger started = new AtomicInteger(), terminated = new AtomicInteger();
        session.onTerminated(terminated::incrementAndGet);
        ExecutorService coordinator = Executors.newSingleThreadExecutor();
        Future<?> future = coordinator.submit(() -> {
            try {
                ReceiveWorkerPool.run(400, 4, session, index -> {
                    started.incrementAndGet();
                    entered.countDown();
                    try { new CountDownLatch(1).await(); }
                    catch (InterruptedException expected) {
                        cleaning.countDown();
                        // Model an I/O resource that needs cleanup after interruption.
                        if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("cleanup timeout");
                    }
                });
            } catch (Exception error) { throw new RuntimeException(error); }
            finally { session.markTerminated(); }
        });
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            session.stop();
            assertTrue(cleaning.await(5, TimeUnit.SECONDS));
            assertEquals(0, terminated.get());
            assertFalse(future.isDone());
            release.countDown();
            future.get(5, TimeUnit.SECONDS);
            assertEquals(1, terminated.get());
            assertEquals(4, started.get());
        } finally {
            release.countDown();
            session.stop();
            coordinator.shutdownNow();
        }
    }

    @Test public void fatalWorkerFailurePropagatesAfterOtherWorkersEnd() throws Exception {
        HttpReceiver.Session session = new HttpReceiver.Session();
        AtomicInteger active = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(4);
        try {
            ReceiveWorkerPool.run(400, 4, session, index -> {
                active.incrementAndGet();
                entered.countDown();
                try {
                    if (!entered.await(5, TimeUnit.SECONDS)) throw new IOException("timeout");
                    if (index == 0) throw new IOException("worker failed");
                } finally { active.decrementAndGet(); }
            });
            fail("expected worker failure");
        } catch (IOException expected) { assertEquals("worker failed", expected.getMessage()); }
        assertEquals(0, active.get());
    }
}
