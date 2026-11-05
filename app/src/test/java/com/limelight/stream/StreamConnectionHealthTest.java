package com.limelight.stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StreamConnectionHealthTest {
    @Test
    public void noFramesYetMustNotTriggerFalseStall() {
        assertFalse(StreamConnectionHealth.isVideoStalled(0L, 60000L, 25000L));
    }

    @Test
    public void activeVideoKeepsStreamAlive() {
        assertFalse(StreamConnectionHealth.isVideoStalled(20000L, 40000L, 25000L));
    }

    @Test
    public void stalledVideoTriggersRecoveryAtThreshold() {
        assertFalse(StreamConnectionHealth.isVideoStalled(15000L, 39999L, 25000L));
        assertTrue(StreamConnectionHealth.isVideoStalled(15000L, 40000L, 25000L));
        assertTrue(StreamConnectionHealth.isVideoStalled(15000L, 80000L, 25000L));
    }

    @Test
    public void badOrResetClockCannotTriggerFalseStall() {
        assertFalse(StreamConnectionHealth.isVideoStalled(40000L, 1000L, 25000L));
        assertFalse(StreamConnectionHealth.isVideoStalled(15000L, 40000L, 0L));
    }

    @Test
    public void firstFrameTimeoutCanRecoverSilentVideoInitialization() {
        assertFalse(StreamConnectionHealth.isVideoInactive(
                0L, 1000L, 10000L, 25000L, true));
        assertTrue(StreamConnectionHealth.isVideoInactive(
                0L, 1000L, 26000L, 25000L, true));
    }

    @Test
    public void inputOnlySessionsDoNotRequireVideoFrames() {
        assertFalse(StreamConnectionHealth.isVideoInactive(
                0L, 1000L, 200000L, 25000L, false));
        assertFalse(StreamConnectionHealth.isVideoInactive(
                1000L, 1000L, 200000L, 25000L, false));
    }

    @Test
    public void nativeHandshakeDoesNotProveVideoRestored() {
        assertFalse(StreamConnectionHealth.isFreshVideoFrame(0L, 10000L, 25000L));
    }

    @Test
    public void recoveryIsConfirmedOnlyByFreshVideo() {
        assertTrue(StreamConnectionHealth.isFreshVideoFrame(1000L, 2000L, 25000L));
        assertFalse(StreamConnectionHealth.isFreshVideoFrame(1000L, 30000L, 25000L));
        assertFalse(StreamConnectionHealth.isFreshVideoFrame(10000L, 9000L, 25000L));
    }

    @Test
    public void freshFrameAfterRecoveryResetsTheWindow() {
        assertTrue(StreamConnectionHealth.isVideoStalled(10000L, 40000L, 25000L));
        assertFalse(StreamConnectionHealth.isVideoStalled(39500L, 40000L, 25000L));
    }
}
