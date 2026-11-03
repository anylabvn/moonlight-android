package com.limelight.binding.input;

import android.view.InputDevice;
import android.view.KeyEvent;

/**
 * Classifies Escape keys before Android can convert an unhandled Escape
 * into a Back navigation event.
 */
public final class StreamEscapeKeyPolicy {
    // Linux evdev KEY_ESC. Android KeyEvent.getScanCode() retains this code
    // for physical keyboards even when a vendor maps it to KEYCODE_BACK.
    private static final int EVDEV_KEY_ESC = 1;

    private StreamEscapeKeyPolicy() {}

    public static boolean isRemoteEscape(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
            return true;
        }

        // Handle keyboard-only Back fallbacks from Escape. Do not steal
        // Android navigation Back or mouse right-button synthetic Back.
        return event.getKeyCode() == KeyEvent.KEYCODE_BACK &&
                event.isFromSource(InputDevice.SOURCE_KEYBOARD) &&
                ((event.getFlags() & KeyEvent.FLAG_FALLBACK) != 0 ||
                        event.getScanCode() == EVDEV_KEY_ESC);
    }
}
