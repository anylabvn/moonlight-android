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
    public void reconnectSuccessResetsBackoff() {
        StreamRetryPolicy policy = new StreamRetryPolicy(new Random(0));
        for (int i = 0; i < 20; i++) policy.nextDelayMillis();
        policy.reset();
        assertEquals(0, policy.getFailureCount());
        long delay = policy.nextDelayMillis();
        assertTrue(delay >= 800 && delay <= 1200);
    }
}
