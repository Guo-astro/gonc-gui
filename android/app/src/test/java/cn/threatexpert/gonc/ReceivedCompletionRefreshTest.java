package cn.threatexpert.gonc;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReceivedCompletionRefreshTest {
    @Test
    public void exitClearsPendingCheckWithoutWaitingForCallback() {
        ReceivedCompletionRefresh refresh = new ReceivedCompletionRefresh();
        long token = refresh.begin();
        assertTrue(refresh.isPending());

        refresh.cancel();

        assertFalse(refresh.isPending());
        assertFalse(refresh.finish(token));
        assertFalse(refresh.isPending());
    }

    @Test
    public void lateCallbackCannotFinishNewDownloadsCheck() {
        ReceivedCompletionRefresh refresh = new ReceivedCompletionRefresh();
        long oldToken = refresh.begin();
        refresh.cancel();
        long currentToken = refresh.begin();

        assertFalse(refresh.finish(oldToken));
        assertTrue(refresh.isPending());
        assertTrue(refresh.finish(currentToken));
        assertFalse(refresh.isPending());
    }

    @Test
    public void terminalCheckCompletesExactlyOnceEvenWhenItsFileResultsAreDiscarded() {
        ReceivedCompletionRefresh refresh = new ReceivedCompletionRefresh();
        long token = refresh.begin();
        // Result freshness is separate from ownership: a stale directory snapshot
        // must not keep the foreground service alive after this check returns.
        assertTrue(refresh.finish(token));
        assertFalse(refresh.finish(token));
        assertFalse(refresh.isPending());
    }

    @Test
    public void ordinaryDirectoryCheckCannotCompleteDownloadsCheck() {
        ReceivedCompletionRefresh refresh = new ReceivedCompletionRefresh();
        long token = refresh.begin();
        assertFalse(refresh.finish(0));
        assertTrue(refresh.isPending());
        assertTrue(refresh.finish(token));
    }
}
