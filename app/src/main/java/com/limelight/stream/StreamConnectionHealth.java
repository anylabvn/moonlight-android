package com.limelight.stream;

/**
 * Health checks use a monotonic clock and only flag stalls after a real video
 * frame has been received. Audio-only/input-only sessions have no video stream
 * to monitor and must not be restarted just because the counter is empty.
 */
public final class StreamConnectionHealth {
    private StreamConnectionHealth() {}

    public static boolean isVideoStalled(long lastFrameMs, long nowMs, long timeoutMs) {
        if (lastFrameMs <= 0 || timeoutMs <= 0 || nowMs < lastFrameMs) {
            return false;
        }
        return nowMs - lastFrameMs >= timeoutMs;
    }
}
