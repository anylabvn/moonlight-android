package com.limelight.nvstream;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Drops callbacks from obsolete Moonlight connection attempts.
 *
 * Moonlight-core and the HTTP preflight may complete asynchronously after the
 * user has disconnected or a newer generation has begun. Such callbacks must
 * never mutate the state of the new session.
 */
public final class GenerationGuardedConnectionListener implements NvConnectionListener {
    private final AtomicLong currentGeneration;
    private final long generation;
    private final NvConnectionListener delegate;

    public GenerationGuardedConnectionListener(AtomicLong currentGeneration,
                                               long generation,
                                               NvConnectionListener delegate) {
        this.currentGeneration = currentGeneration;
        this.generation = generation;
        this.delegate = delegate;
    }

    public boolean isCurrent() {
        return currentGeneration.get() == generation;
    }

    @Override
    public void stageStarting(String stage) {
        if (isCurrent()) delegate.stageStarting(stage);
    }

    @Override
    public void stageComplete(String stage) {
        if (isCurrent()) delegate.stageComplete(stage);
    }

    @Override
    public boolean stageFailed(String stage, int portFlags, int errorCode) {
        return isCurrent() && delegate.stageFailed(stage, portFlags, errorCode);
    }

    @Override
    public void connectionStarted() {
        if (isCurrent()) delegate.connectionStarted();
    }

    @Override
    public void connectionTerminated(int errorCode) {
        if (isCurrent()) delegate.connectionTerminated(errorCode);
    }

    @Override
    public void connectionStatusUpdate(int connectionStatus) {
        if (isCurrent()) delegate.connectionStatusUpdate(connectionStatus);
    }

    @Override
    public void displayMessage(String message) {
        if (isCurrent()) delegate.displayMessage(message);
    }

    @Override
    public void displayTransientMessage(String message) {
        if (isCurrent()) delegate.displayTransientMessage(message);
    }

    @Override
    public void rumble(short controllerNumber, short lowFreqMotor, short highFreqMotor) {
        if (isCurrent()) delegate.rumble(controllerNumber, lowFreqMotor, highFreqMotor);
    }

    @Override
    public void rumbleTriggers(short controllerNumber, short leftFreqMotor, short rightFreqMotor) {
        if (isCurrent()) delegate.rumbleTriggers(controllerNumber, leftFreqMotor, rightFreqMotor);
    }

    @Override
    public void setHdrMode(boolean enabled, byte[] hdrMetadata) {
        if (isCurrent()) delegate.setHdrMode(enabled, hdrMetadata);
    }

    @Override
    public void setMotionEventState(short controllerNumber, byte motionType, short reportRateHz) {
        if (isCurrent()) delegate.setMotionEventState(controllerNumber, motionType, reportRateHz);
    }

    @Override
    public void setControllerLED(short controllerNumber, byte r, byte g, byte b) {
        if (isCurrent()) delegate.setControllerLED(controllerNumber, r, g, b);
    }
}
