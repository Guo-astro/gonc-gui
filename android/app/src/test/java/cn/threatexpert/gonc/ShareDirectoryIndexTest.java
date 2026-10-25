package cn.threatexpert.gonc;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ShareDirectoryIndexTest {
    @Test public void fourHundredFilesAndThreeLookupsEachOnlyLoadDirectoryOnce() throws Exception {
        AtomicInteger queries = new AtomicInteger();
        List<String> files = new ArrayList<>();
        for (int i = 0; i < 400; i++) files.add("image-" + i + ".jpg");
        ShareDirectoryIndex<String> index = new ShareDirectoryIndex<>(() -> {
            queries.incrementAndGet();
            return files;
        }, name -> name);
        assertEquals(files, index.refresh()); // Initial remote listing primes the index.
        for (String file : files) {
            for (int call = 0; call < 3; call++) assertEquals(file, index.find(file, false));
        }
        assertEquals(1, queries.get());
    }

    @Test public void firstLookupWithoutListingLoadsOnlyOnce() throws Exception {
        AtomicInteger queries = new AtomicInteger();
        ShareDirectoryIndex<String> index = new ShareDirectoryIndex<>(() -> {
            queries.incrementAndGet();
            return Arrays.asList("a");
        }, name -> name);
        assertEquals("a", index.find("a", false));
        assertEquals("a", index.find("a", false));
        assertEquals(1, queries.get());
    }

    @Test public void missingFileRefreshesOnceToFindNewOrRenamedEntry() throws Exception {
        List<String> files = new ArrayList<>(Arrays.asList("old"));
        AtomicInteger queries = new AtomicInteger();
        ShareDirectoryIndex<String> index = new ShareDirectoryIndex<>(() -> {
            queries.incrementAndGet();
            return files;
        }, name -> name);
        index.refresh();
        files.clear();
        files.add("new");
        assertEquals("new", index.find("new", false));
        assertEquals(2, queries.get());
        assertNull(index.find("old", false));
        assertEquals(3, queries.get());
    }

    @Test public void failedOpenRecoveryReplacesCachedLocation() throws Exception {
        List<String> locations = new ArrayList<>(Arrays.asList("uri-old"));
        ShareDirectoryIndex<String> index = new ShareDirectoryIndex<>(() -> locations, value -> "photo");
        assertEquals("uri-old", index.find("photo", false));
        locations.set(0, "uri-new");
        assertEquals("uri-new", index.find("photo", true));
        assertEquals("uri-new", index.find("photo", false));
    }

    @Test public void explicitListingRefreshDropsDeletedEntriesAndReturnsDetachedList() throws Exception {
        List<String> files = new ArrayList<>(Arrays.asList("a", "b"));
        ShareDirectoryIndex<String> index = new ShareDirectoryIndex<>(() -> files, name -> name);
        List<String> first = index.refresh();
        files.remove("a");
        assertEquals(Arrays.asList("b"), index.refresh());
        first.clear();
        assertEquals("b", index.find("b", false));
        assertNull(index.find("a", false));
    }

    @Test public void queryFailureDoesNotLeaveStaleEntries() throws Exception {
        AtomicInteger queries = new AtomicInteger();
        ShareDirectoryIndex<String> index = new ShareDirectoryIndex<>(() -> {
            int call = queries.incrementAndGet();
            if (call == 2) throw new Exception("provider unavailable");
            return Arrays.asList(call == 1 ? "old" : "new");
        }, name -> name);
        assertEquals("old", index.find("old", false));
        try {
            index.refresh();
            fail("expected provider failure");
        } catch (Exception expected) {
            assertEquals("provider unavailable", expected.getMessage());
        }
        assertEquals("new", index.find("new", false));
        assertEquals(3, queries.get());
    }

    @Test public void absentEntryInFreshIndexDoesNotQueryTwice() throws Exception {
        AtomicInteger queries = new AtomicInteger();
        ShareDirectoryIndex<String> index = new ShareDirectoryIndex<>(() -> {
            queries.incrementAndGet();
            return new ArrayList<>();
        }, name -> name);
        assertNull(index.find("absent", false));
        assertEquals(1, queries.get());
        assertNull(index.find("absent", true));
        assertEquals(2, queries.get());
    }

    @Test public void concurrentDownloadsShareOneDirectoryQuery() throws Exception {
        AtomicInteger queries = new AtomicInteger();
        CountDownLatch queryStarted = new CountDownLatch(1);
        CountDownLatch releaseQuery = new CountDownLatch(1);
        ShareDirectoryIndex<String> index = new ShareDirectoryIndex<>(() -> {
            queries.incrementAndGet();
            queryStarted.countDown();
            if (!releaseQuery.await(5, TimeUnit.SECONDS)) throw new Exception("test timeout");
            return Arrays.asList("photo");
        }, name -> name);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) results.add(pool.submit(() -> index.find("photo", false)));
            assertTrue(queryStarted.await(5, TimeUnit.SECONDS));
            releaseQuery.countDown();
            for (Future<String> result : results) assertEquals("photo", result.get(5, TimeUnit.SECONDS));
            assertEquals(1, queries.get());
        } finally {
            releaseQuery.countDown();
            pool.shutdownNow();
        }
    }
}
