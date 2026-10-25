package cn.threatexpert.gonc;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HttpReceiverSessionTest {
    @Test public void stopDisconnectsEveryActiveConnectionAndRejectsLateAttach() throws Exception {
        HttpReceiver.Session session = new HttpReceiver.Session();
        RecordingConnection[] connections = new RecordingConnection[4];
        for (int i = 0; i < 4; i++) {
            connections[i] = new RecordingConnection();
            session.attach(connections[i]);
        }
        session.stop();
        session.stop();
        for (RecordingConnection connection : connections) assertEquals(1, connection.closes.get());
        RecordingConnection late = new RecordingConnection();
        session.attach(late);
        assertEquals(1, late.closes.get());
    }

    @Test public void detachingOneConnectionDoesNotHideOthers() throws Exception {
        HttpReceiver.Session session = new HttpReceiver.Session();
        RecordingConnection first = new RecordingConnection(), second = new RecordingConnection();
        session.attach(first);
        session.attach(second);
        session.detach(first);
        session.stop();
        assertEquals(1, first.closes.get());
        assertEquals(1, second.closes.get());
    }

    @Test public void brokenDisconnectDoesNotPreventOtherConnectionsClosing() throws Exception {
        HttpReceiver.Session session = new HttpReceiver.Session();
        RecordingConnection broken = new RecordingConnection() {
            @Override public void disconnect() { throw new IllegalStateException("test"); }
        };
        RecordingConnection good = new RecordingConnection();
        session.attach(broken);
        session.attach(good);
        session.stop();
        assertEquals(1, good.closes.get());
    }

    @Test public void executorAttachedAfterStopIsImmediatelyShutdown() {
        HttpReceiver.Session session = new HttpReceiver.Session();
        session.stop();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            session.attachExecutor(pool);
            assertTrue(pool.isShutdown());
        } finally { pool.shutdownNow(); }
    }

    private static class RecordingConnection extends HttpURLConnection {
        final AtomicInteger closes = new AtomicInteger();
        RecordingConnection() throws Exception { super(new URL("http://127.0.0.1/file")); }
        @Override public void disconnect() { closes.incrementAndGet(); }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
    @Test
    public void stopDoesNotReportTerminationUntilWorkerActuallyEnds() {
        HttpReceiver.Session session = new HttpReceiver.Session();
        AtomicInteger terminations = new AtomicInteger();
        session.onTerminated(terminations::incrementAndGet);

        session.stop();
        assertEquals(0, terminations.get());

        session.markTerminated();
        session.markTerminated();
        assertEquals(1, terminations.get());
    }

    @Test
    public void listenerRegisteredAfterTerminationRunsExactlyOnce() {
        HttpReceiver.Session session = new HttpReceiver.Session();
        session.markTerminated();
        AtomicInteger terminations = new AtomicInteger();

        session.onTerminated(terminations::incrementAndGet);
        assertEquals(1, terminations.get());
    }
}
