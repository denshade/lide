package lide;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Application-wide Back/Forward shortcuts. Menu-item accelerators only fire
 * while the popup is showing, so these keys are dispatched at the window level.
 */
final class NavigationHotkeys {
    enum Action {
        BACK,
        FORWARD,
        TO_TEST,
        TO_IMPLEMENTATION
    }

    private NavigationHotkeys() {
    }

    static Action actionFor(int keyCode, int modifiersEx) {
        if (modifiersEx == KeyEvent.ALT_DOWN_MASK) {
            return switch (keyCode) {
                case KeyEvent.VK_LEFT -> Action.BACK;
                case KeyEvent.VK_RIGHT -> Action.FORWARD;
                default -> null;
            };
        }
        int ctrlShift = KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK;
        if (modifiersEx == ctrlShift) {
            return switch (keyCode) {
                case KeyEvent.VK_T -> Action.TO_TEST;
                case KeyEvent.VK_I -> Action.TO_IMPLEMENTATION;
                default -> null;
            };
        }
        return null;
    }

    static boolean dispatch(KeyEvent event, Window window, Consumer<Action> run) {
        if (event == null || window == null || run == null) {
            return false;
        }
        if (event.getID() != KeyEvent.KEY_PRESSED || event.isConsumed()) {
            return false;
        }
        Action action = actionFor(event.getKeyCode(), event.getModifiersEx());
        if (action == null || !belongsTo(event, window)) {
            return false;
        }
        run.accept(action);
        event.consume();
        return true;
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
