package cn.threatexpert.gonc;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class ReceiveProgressTest {
    @Test public void parallelFilesDoNotOverwriteEachOthersProgress() {
        ReceiveProgress p = new ReceiveProgress(new long[]{100, 200});
        p.update(0, 30, 30, 100);
        p.update(1, 70, 70, 200);
        p.update(0, 50, 50, 100);
        assertEquals(120, p.snapshot().doneBytes);
        assertEquals(120, p.snapshot().networkBytes);
        assertEquals(300, p.snapshot().totalBytes);
        assertEquals(0, p.snapshot().doneFiles);
    }

    @Test public void resumeAndSkipOnlyCountTransferredBytesAsNetwork() {
        ReceiveProgress p = new ReceiveProgress(new long[]{100, 200});
        p.finish(0, 100, 0, 100, true, false, false);
        p.update(1, 150, 50, 200);
        p.finish(1, 200, 100, 200, false, true, false);
        ReceiveProgress.Snapshot s = p.snapshot();
        assertEquals(300, s.doneBytes);
        assertEquals(100, s.networkBytes);
        assertEquals(1, s.skippedFiles);
        assertEquals(1, s.resumedFiles);
        assertEquals(2, s.doneFiles);
    }

    @Test public void failedPartialDownloadIsCountedOnceAndLateEventsAreIgnored() {
        ReceiveProgress p = new ReceiveProgress(new long[]{100});
        p.finish(0, 30, 40, 100, false, false, true);
        p.finish(0, 100, 100, 100, false, false, false);
        p.update(0, 90, 90, 100);
        assertEquals(1, p.snapshot().doneFiles);
        assertEquals(1, p.snapshot().failedFiles);
        assertEquals(30, p.snapshot().doneBytes);
        assertEquals(40, p.snapshot().networkBytes);
    }

    @Test public void fullFallbackResetsLogicalBytesButPreservesEarlierNetworkTraffic() {
        ReceiveProgress p = new ReceiveProgress(new long[]{100});
        p.update(0, 90, 20, 100);
        p.update(0, 0, 20, 100);
        assertEquals(0, p.snapshot().doneBytes);
        assertEquals(20, p.snapshot().networkBytes);
        p.finish(0, 100, 120, 100, false, false, false);
        assertEquals(100, p.snapshot().doneBytes);
        assertEquals(120, p.snapshot().networkBytes);
    }

    @Test public void remoteSizeChangesUpdateTotalWithoutAffectingOtherFiles() {
        ReceiveProgress p = new ReceiveProgress(new long[]{100, 200});
        p.update(0, 50, 0, 150);
        assertEquals(350, p.snapshot().totalBytes);
        p.update(1, 10, 10, 80);
        assertEquals(230, p.snapshot().totalBytes);
    }

    @Test public void fourWorkersFinishFourHundredFilesWithoutLostCounts() throws Exception {
        long[] sizes = new long[400];
        java.util.Arrays.fill(sizes, 100);
        ReceiveProgress p = new ReceiveProgress(sizes);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 400; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    p.update(index, 50, 50, 100);
                    p.finish(index, 100, 100, 100, false, false, false);
                }));
            }
            for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
            assertEquals(400, p.snapshot().doneFiles);
            assertEquals(40000, p.snapshot().doneBytes);
            assertEquals(40000, p.snapshot().networkBytes);
        } finally { pool.shutdownNow(); }
    }
}
