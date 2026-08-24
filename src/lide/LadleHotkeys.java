package lide;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Application-wide Ladle shortcuts. Menu-item accelerators only fire while
 * the popup is showing, so these keys are dispatched at the window level.
 */
final class LadleHotkeys {
    private LadleHotkeys() {
    }

    static String commandFor(int keyCode, int modifiersEx) {
        if (modifiersEx != 0) {
            return null;
        }
        return switch (keyCode) {
            case KeyEvent.VK_F4 -> LadleCommand.DEPENDENCY;
            case KeyEvent.VK_F5 -> LadleCommand.BUILD;
            case KeyEvent.VK_F6 -> LadleCommand.TEST;
            default -> null;
        };
    }

    static boolean dispatch(KeyEvent event, Window window, Consumer<String> run) {
        if (event == null || window == null || run == null) {
            return false;
        }
        if (event.getID() != KeyEvent.KEY_PRESSED || event.isConsumed()) {
            return false;
        }
        String command = commandFor(event.getKeyCode(), event.getModifiersEx());
        if (command == null || !belongsTo(event, window)) {
            return false;
        }
        run.accept(command);
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
