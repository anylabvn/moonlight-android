package com.limelight.stream;

import static org.junit.Assert.*;

import org.junit.Test;

public class StreamReconnectCycleTest {
    @Test
    public void newConnectCannotStartBeforeOldSessionHasStopped() {
        StreamReconnectCycle cycle = new StreamReconnectCycle();
        long round = cycle.beginDisconnect();
        assertEquals(StreamReconnectCycle.Phase.STOPPING, cycle.getPhase());
        assertFalse(cycle.beginConnect(round));
        assertFalse(cycle.isWaiting(round));
        assertTrue(cycle.disconnected(round));
        assertTrue(cycle.isWaiting(round));
        assertTrue(cycle.beginConnect(round));
        assertEquals(StreamReconnectCycle.Phase.CONNECTING, cycle.getPhase());
        cycle.connected();
        assertEquals(StreamReconnectCycle.Phase.IDLE, cycle.getPhase());
    }

    @Test
    public void duplicateTeardownCannotSpawnTwoConnections() {
        StreamReconnectCycle cycle = new StreamReconnectCycle();
        long round = cycle.beginDisconnect();
        assertEquals(-1L, cycle.beginDisconnect());
        assertTrue(cycle.disconnected(round));
        assertFalse(cycle.disconnected(round));
        assertEquals(-1L, cycle.beginDisconnect());
        assertTrue(cycle.beginConnect(round));
        assertFalse(cycle.beginConnect(round));
    }

    @Test
    public void abandonedAttemptMayBeStoppedThenStartedInANewRound() {
        StreamReconnectCycle cycle = new StreamReconnectCycle();
        long first = cycle.beginDisconnect();
        assertTrue(cycle.disconnected(first));
        assertTrue(cycle.beginConnect(first));
        long second = cycle.beginDisconnect();
        assertTrue(second > first);
        assertFalse(cycle.disconnected(first));
        assertFalse(cycle.beginConnect(first));
        assertTrue(cycle.disconnected(second));
        assertTrue(cycle.beginConnect(second));
    }

    @Test
    public void activityCloseInvalidatesPendingCleanupAndRetry() {
        StreamReconnectCycle cycle = new StreamReconnectCycle();
        long first = cycle.beginDisconnect();
        cycle.close();
        assertFalse(cycle.disconnected(first));
        assertFalse(cycle.beginConnect(first));
        assertEquals(-1L, cycle.beginDisconnect());
        assertEquals(StreamReconnectCycle.Phase.CLOSED, cycle.getPhase());
    }

    @Test
    public void nativeHandshakeBeforeStoppingEndsCannotChangeCycle() {
        StreamReconnectCycle cycle = new StreamReconnectCycle();
        long round = cycle.beginDisconnect();
        cycle.connected();
        assertEquals(StreamReconnectCycle.Phase.STOPPING, cycle.getPhase());
        assertTrue(cycle.disconnected(round));
        assertEquals(StreamReconnectCycle.Phase.WAITING, cycle.getPhase());
    }
}
