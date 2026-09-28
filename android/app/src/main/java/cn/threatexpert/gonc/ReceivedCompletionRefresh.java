package cn.threatexpert.gonc;

/** Main-thread ownership of the local-file check after a download ends. */
final class ReceivedCompletionRefresh {
    private long generation;
    private boolean pending;

    boolean isPending() {
        return pending;
    }

    long begin() {
        pending = true;
        return ++generation;
    }

    void cancel() {
        generation++;
        pending = false;
    }

    boolean finish(long token) {
        if (!pending || token == 0 || token != generation) {
            return false;
        }
        pending = false;
        return true;
    }
}
