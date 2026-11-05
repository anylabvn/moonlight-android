package com.limelight.stream;

import java.util.Random;

/**
 * Reconnect indefinitely for transient failures, with a bounded retry rate.
 * A network-available callback may accelerate the next attempt, but it never
 * creates a second in-flight connection.
 */
public final class StreamRetryPolicy {
    private static final long[] BASE_DELAYS_MS = {1000, 2000, 4000, 8000, 15000, 30000};
    private final Random random;
    private int failures;
    private static final long STABLE_CONNECTION_MS = 60_000L;

    public StreamRetryPolicy() {
        this(new Random());
    }

    StreamRetryPolicy(Random random) {
        this.random = random;
    }

    public long nextDelayMillis() {
        int index = Math.min(failures, BASE_DELAYS_MS.length - 1);
        failures++;
        // +/- 20% jitter prevents reconnect storms if many clients lose a host.
        return Math.round(BASE_DELAYS_MS[index] * (0.8 + 0.4 * random.nextDouble()));
    }

    public void reset() {
        failures = 0;
    }

    /**
     * A connection that survives only a few seconds isn't stable enough to
     * reset exponential backoff. This prevents repeated one-second reconnects
     * when the server or network is flapping.
     */
    public void onStreamEnded(long connectedAtMs, long endedAtMs) {
        if (connectedAtMs > 0 && endedAtMs >= connectedAtMs &&
                endedAtMs - connectedAtMs >= STABLE_CONNECTION_MS) {
            reset();
        }
    }

    public int getFailureCount() {
        return failures;
    }
}
