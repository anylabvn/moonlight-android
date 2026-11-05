package com.limelight.nvstream;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayDeque;

public class GenerationGuardedConnectionListenerTest {
    private static final class Recorder implements NvConnectionListener {
        int started, terminated, failed, stage;
        public void stageStarting(String value) { stage++; }
        public void stageComplete(String value) { stage++; }
        public boolean stageFailed(String value, int portFlags, int code) { failed++; return true; }
        public void connectionStarted() { started++; }
        public void connectionTerminated(int errorCode) { terminated++; }
        public void connectionStatusUpdate(int value) {}
        public void displayMessage(String value) {}
        public void displayTransientMessage(String value) {}
        public void rumble(short n, short low, short high) {}
        public void rumbleTriggers(short n, short low, short high) {}
        public void setHdrMode(boolean enabled, byte[] metadata) {}
        public void setMotionEventState(short n, byte type, short hz) {}
        public void setControllerLED(short n, byte r, byte g, byte b) {}
    }

    @Test
    public void oldCallbacksCannotModifyNewSession() {
        AtomicLong generation = new AtomicLong(1);
        Recorder first = new Recorder();
        GenerationGuardedConnectionListener old =
                new GenerationGuardedConnectionListener(generation, 1, first);
        old.connectionStarted();
        assertEquals(1, first.started);
        generation.incrementAndGet();
        old.connectionTerminated(123);
        old.stageStarting("video");
        assertFalse(old.stageFailed("video", 0, -408));
        assertEquals(0, first.terminated);
        assertEquals(0, first.stage);
        assertEquals(0, first.failed);
        Recorder second = new Recorder();
        GenerationGuardedConnectionListener active =
                new GenerationGuardedConnectionListener(generation, 2, second);
        active.connectionStarted();
        active.connectionTerminated(-408);
        assertEquals(1, second.started);
        assertEquals(1, second.terminated);
    }

    @Test
    public void cancelledAttemptCannotRequestAnotherRetry() {
        AtomicLong generation = new AtomicLong(8);
        Recorder recorder = new Recorder();
        GenerationGuardedConnectionListener guarded =
                new GenerationGuardedConnectionListener(generation, 8, recorder);
        assertTrue(guarded.stageFailed("network", 1, -408));
        generation.incrementAndGet();
        assertFalse(guarded.stageFailed("network", 1, -408));
        assertEquals(1, recorder.failed);
    }

    @Test
    public void queuedLifecycleCallbackIsDroppedAfterCancellation() {
        AtomicLong current = new AtomicLong(3);
        Recorder recorder = new Recorder();
        ArrayDeque<Runnable> pending = new ArrayDeque<>();
        GenerationGuardedConnectionListener listener =
                new GenerationGuardedConnectionListener(current, 3, recorder, pending::addLast);
        listener.connectionStarted();
        listener.connectionTerminated(-408);
        assertEquals(2, pending.size());
        current.incrementAndGet();
        while (!pending.isEmpty()) pending.removeFirst().run();
        assertEquals(0, recorder.started);
        assertEquals(0, recorder.terminated);
    }
}
