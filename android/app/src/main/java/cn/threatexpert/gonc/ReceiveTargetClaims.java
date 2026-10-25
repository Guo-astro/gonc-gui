package cn.threatexpert.gonc;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Keep reservations until the batch ends, including failed/partially written files. */
final class ReceiveTargetClaims {
    private final Map<String, String> paths = new HashMap<>();
    private final Map<String, String> uris = new HashMap<>();

    synchronized void claimPath(String destination, String source) throws IOException {
        check(paths, destination, source);
        paths.put(destination, source);
    }

    synchronized void checkUri(String uri, String source) throws IOException {
        check(uris, uri, source);
    }

    synchronized void claimUri(String uri, String source) throws IOException {
        check(uris, uri, source);
        uris.put(uri, source);
    }

    private static void check(Map<String, String> claims, String key, String source) throws IOException {
        String owner = claims.get(key);
        if (owner != null && !owner.equals(source)) {
            throw new IOException("Destination conflicts with another file in this batch: " + owner);
        }
    }
}
