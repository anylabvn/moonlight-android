package com.limelight.stream;

/**
 * A single reconnect round is a strict stop -> wait -> connect transaction.
 * The round token prevents an old asynchronous cleanup completion from
 * authorizing another connection after cancellation or a newer round.
 *
 * Does not own MoonBridge or an Activity; it only controls the handoff.
 */
public final class StreamReconnectCycle {
    public enum Phase { IDLE, STOPPING, WAITING, CONNECTING, CLOSED }

    private long round;
    private Phase phase = Phase.IDLE;

    public synchronized long beginDisconnect() {
        if (phase == Phase.CLOSED || phase == Phase.STOPPING ||
                phase == Phase.WAITING) return -1;
        phase = Phase.STOPPING;
        return ++round;
    }

    public synchronized boolean disconnected(long expectedRound) {
        if (phase != Phase.STOPPING || expectedRound != round) return false;
        phase = Phase.WAITING;
        return true;
    }

    public synchronized boolean beginConnect(long expectedRound) {
        if (phase != Phase.WAITING || expectedRound != round) return false;
        phase = Phase.CONNECTING;
        return true;
    }

    public synchronized boolean isWaiting(long expectedRound) {
        return phase == Phase.WAITING && expectedRound == round;
    }

    public synchronized long currentRound() {
        return round;
    }

    public synchronized void connected() {
        if (phase == Phase.CONNECTING || phase == Phase.IDLE) {
            phase = Phase.IDLE;
        }
    }

    public synchronized void close() {
        round++;
        phase = Phase.CLOSED;
    }

    public synchronized Phase getPhase() {
        return phase;
    }
}
