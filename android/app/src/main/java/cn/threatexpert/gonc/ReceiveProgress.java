package cn.threatexpert.gonc;

/** Thread-safe accounting: each file owns one slot, including partial failed downloads. */
final class ReceiveProgress {
    private final long[] expected, done, network;
    private final boolean[] finished;
    private long totalBytes, doneBytes, networkBytes;
    private int doneFiles, skippedFiles, resumedFiles, failedFiles;

    ReceiveProgress(long[] sizes) {
        expected = sizes.clone();
        done = new long[sizes.length];
        network = new long[sizes.length];
        finished = new boolean[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            expected[i] = Math.max(0, sizes[i]);
            totalBytes += expected[i];
        }
    }

    synchronized void update(int index, long bytes, long transferred, long size) {
        if (finished[index]) return;
        long nextSize = Math.max(0, size);
        totalBytes += nextSize - expected[index];
        expected[index] = nextSize;
        long nextDone = Math.max(0, bytes);
        doneBytes += nextDone - done[index];
        done[index] = nextDone;
        long nextNetwork = Math.max(network[index], transferred);
        networkBytes += nextNetwork - network[index];
        network[index] = nextNetwork;
    }

    synchronized void finish(int index, long bytes, long transferred, long size,
                             boolean skipped, boolean resumed, boolean failed) {
        if (finished[index]) return;
        update(index, bytes, transferred, size);
        finished[index] = true;
        doneFiles++;
        if (skipped) skippedFiles++;
        if (resumed) resumedFiles++;
        if (failed) failedFiles++;
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(expected.length, doneFiles, totalBytes, doneBytes, networkBytes,
                skippedFiles, resumedFiles, failedFiles);
    }

    static final class Snapshot {
        final int totalFiles, doneFiles, skippedFiles, resumedFiles, failedFiles;
        final long totalBytes, doneBytes, networkBytes;
        Snapshot(int totalFiles, int doneFiles, long totalBytes, long doneBytes, long networkBytes,
                 int skippedFiles, int resumedFiles, int failedFiles) {
            this.totalFiles = totalFiles;
            this.doneFiles = doneFiles;
            this.totalBytes = totalBytes;
            this.doneBytes = doneBytes;
            this.networkBytes = networkBytes;
            this.skippedFiles = skippedFiles;
            this.resumedFiles = resumedFiles;
            this.failedFiles = failedFiles;
        }
    }
}
