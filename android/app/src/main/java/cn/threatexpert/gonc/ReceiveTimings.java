package cn.threatexpert.gonc;

import java.util.LinkedHashMap;
import java.util.Map;

/** Accumulated worker time (may exceed wall time when transfers overlap). */
final class ReceiveTimings {
    private final long started = System.nanoTime();
    private final Map<String, long[]> stages = new LinkedHashMap<>();

    synchronized void record(String stage, long startNanos) {
        long elapsed = Math.max(0, System.nanoTime() - startNanos);
        long[] values = stages.get(stage);
        if (values == null) {
            values = new long[3];
            stages.put(stage, values);
        }
        values[0]++;
        values[1] += elapsed;
        values[2] = Math.max(values[2], elapsed);
    }

    synchronized String summary(int workers) {
        StringBuilder out = new StringBuilder("Receive timing: workers=").append(workers)
                .append(" wallMs=").append((System.nanoTime() - started) / 1_000_000L);
        for (Map.Entry<String, long[]> entry : stages.entrySet()) {
            long[] v = entry.getValue();
            out.append("; ").append(entry.getKey()).append(" count=").append(v[0])
                    .append(" avgMs=").append(v[1] / v[0] / 1_000_000L)
                    .append(" maxMs=").append(v[2] / 1_000_000L);
        }
        return out.toString();
    }
}
