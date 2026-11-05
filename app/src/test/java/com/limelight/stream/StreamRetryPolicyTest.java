package com.limelight.stream;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class StreamRetryPolicyTest {
    @Test
    public void retryIsUnboundedButBackoffIsCapped() {
        StreamRetryPolicy policy = new StreamRetryPolicy(new Random(42));
        for (int i = 0; i < 1000; i++) {
            long delay = policy.nextDelayMillis();
            assertTrue(delay >= 800);
            assertTrue(delay <= 36000);
        }
        assertEquals(1000, policy.getFailureCount());
    }

    @Test
    public void shortConnectionDoesNotResetFailureBackoff() {
        StreamRetryPolicy policy = new StreamRetryPolicy(new Random(0));
        for (int i = 0; i < 6; i++) policy.nextDelayMillis();
        policy.onStreamEnded(1000L, 12000L);
        assertEquals(6, policy.getFailureCount());
    }

    @Test
    public void stableConnectionDoesResetFailureBackoff() {
        StreamRetryPolicy policy = new StreamRetryPolicy(new Random(0));
        for (int i = 0; i < 6; i++) policy.nextDelayMillis();
        policy.onStreamEnded(1000L, 62000L);
        assertEquals(0, policy.getFailureCount());
    }

    @Test
    public void invalidOrAbsentConnectedTimeCannotResetBackoff() {
        StreamRetryPolicy policy = new StreamRetryPolicy(new Random(0));
        policy.nextDelayMillis();
        policy.onStreamEnded(0L, 80000L);
        policy.onStreamEnded(90000L, 80000L);
        assertEquals(1, policy.getFailureCount());
    }

    @Test
    public void reconnectSuccessResetsBackoff() {
        StreamRetryPolicy policy = new StreamRetryPolicy(new Random(0));
        for (int i = 0; i < 20; i++) policy.nextDelayMillis();
        policy.reset();
        assertEquals(0, policy.getFailureCount());
        long delay = policy.nextDelayMillis();
        assertTrue(delay >= 800 && delay <= 1200);
    }
}
