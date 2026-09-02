package lide;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.SwingUtilities;

/**
 * Detects a double tap of Shift (IntelliJ-style) at the window level.
 * The second press must follow a release of the first, within
 * {@link #TIMEOUT_MS}, with no other keys in between.
 */
final class DoubleShiftHotkeys {
    static final long TIMEOUT_MS = 400;

    private long firstPressWhen;
    private boolean awaitingSecond;
    private boolean releasedSinceFirst;

    boolean dispatch(KeyEvent event, Window window, Runnable open) {
        if (event == null || window == null || open == null || event.isConsumed()) {
            return false;
        }
        if (!belongsTo(event, window)) {
            return false;
        }
        if (event.getID() == KeyEvent.KEY_RELEASED && event.getKeyCode() == KeyEvent.VK_SHIFT) {
            if (awaitingSecond) {
                releasedSinceFirst = true;
            }
            return false;
        }
        if (event.getID() != KeyEvent.KEY_PRESSED) {
            return false;
        }
        if (event.getKeyCode() != KeyEvent.VK_SHIFT || hasOtherModifiers(event)) {
            reset();
            return false;
        }
        long now = event.getWhen();
        if (awaitingSecond && releasedSinceFirst && now - firstPressWhen <= TIMEOUT_MS) {
            reset();
            event.consume();
            open.run();
            return true;
        }
        awaitingSecond = true;
        releasedSinceFirst = false;
        firstPressWhen = now;
        return false;
    }

    void reset() {
        awaitingSecond = false;
        releasedSinceFirst = false;
        firstPressWhen = 0;
    }

    private static boolean hasOtherModifiers(KeyEvent event) {
        return (event.getModifiersEx() & ~InputEvent.SHIFT_DOWN_MASK) != 0;
    }

    private static boolean belongsTo(KeyEvent event, Window window) {
        if (!(event.getSource() instanceof Component source)) {
            return false;
        }
        if (source == window) {
            return true;
        }
        return SwingUtilities.getWindowAncestor(source) == window;
    }
}
