package com.limelight.binding.input;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.InputDevice;
import android.view.KeyEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Regression for physical Escape opening Artemis's session menu. */
@Config(sdk = {33})
@RunWith(RobolectricTestRunner.class)
public class StreamEscapeKeyPolicyTest {
    private static KeyEvent key(int action, int code, int scanCode,
                                int flags, int source) {
        return new KeyEvent(100L, 101L, action, code, 0, 0,
                4, scanCode, flags, source);
    }

    @Test
    public void physicalEscapeIsRecognizedOnPressAndRelease() {
        assertTrue(StreamEscapeKeyPolicy.isRemoteEscape(
                key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 1, 0,
                        InputDevice.SOURCE_KEYBOARD)));
        assertTrue(StreamEscapeKeyPolicy.isRemoteEscape(
                key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 1, 0,
                        InputDevice.SOURCE_KEYBOARD)));
    }

    @Test
    public void keyboardBackFallbackIsRecognizedAsEscape() {
        assertTrue(StreamEscapeKeyPolicy.isRemoteEscape(
                key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0,
                        KeyEvent.FLAG_FALLBACK, InputDevice.SOURCE_KEYBOARD)));
        assertTrue(StreamEscapeKeyPolicy.isRemoteEscape(
                key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0,
                        KeyEvent.FLAG_FALLBACK, InputDevice.SOURCE_KEYBOARD)));
    }

    @Test
    public void vendorMappingEscapeScanCodeToBackIsRecognized() {
        assertTrue(StreamEscapeKeyPolicy.isRemoteEscape(
                key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 1, 0,
                        InputDevice.SOURCE_KEYBOARD)));
    }

    @Test
    public void androidBackAndMouseBackRemainUnchanged() {
        assertFalse(StreamEscapeKeyPolicy.isRemoteEscape(
                key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 0,
                        InputDevice.SOURCE_KEYBOARD)));
        assertFalse(StreamEscapeKeyPolicy.isRemoteEscape(
                key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 158, 0,
                        InputDevice.SOURCE_KEYBOARD)));
        assertFalse(StreamEscapeKeyPolicy.isRemoteEscape(
                key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 0,
                        InputDevice.SOURCE_DPAD)));
        assertFalse(StreamEscapeKeyPolicy.isRemoteEscape(
                key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 1,
                        KeyEvent.FLAG_FALLBACK, InputDevice.SOURCE_MOUSE)));
    }

    @Test
    public void unrelatedKeyboardKeysNeverTriggerEscapeHandling() {
        assertFalse(StreamEscapeKeyPolicy.isRemoteEscape(
                key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 14, 0,
                        InputDevice.SOURCE_KEYBOARD)));
        assertFalse(StreamEscapeKeyPolicy.isRemoteEscape(
                key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU, 139, 0,
                        InputDevice.SOURCE_KEYBOARD)));
    }
}
