package lide;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

/**
 * Tests for application-wide Navigate hotkeys.
 */
public final class NavigationHotkeysTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testActionMapping();
        testIgnoresWrongModifiers();
        testIgnoresUnknownKeys();
        SwingUtilities.invokeAndWait(() -> {
            testDispatchFromEditorComponent();
            testDispatchIgnoresOtherWindows();
            testDispatchIgnoresKeyRelease();
        });
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testActionMapping() {
        assertEqual("alt left", NavigationHotkeys.Action.BACK,
                NavigationHotkeys.actionFor(KeyEvent.VK_LEFT, KeyEvent.ALT_DOWN_MASK));
        assertEqual("alt right", NavigationHotkeys.Action.FORWARD,
                NavigationHotkeys.actionFor(KeyEvent.VK_RIGHT, KeyEvent.ALT_DOWN_MASK));
    }

    private static void testIgnoresWrongModifiers() {
        assertEqual("plain left", null, NavigationHotkeys.actionFor(KeyEvent.VK_LEFT, 0));
        assertEqual("ctrl alt left", null,
                NavigationHotkeys.actionFor(KeyEvent.VK_LEFT,
                        KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK));
        assertEqual("shift alt right", null,
                NavigationHotkeys.actionFor(KeyEvent.VK_RIGHT,
                        KeyEvent.SHIFT_DOWN_MASK | KeyEvent.ALT_DOWN_MASK));
    }

    private static void testIgnoresUnknownKeys() {
        assertEqual("alt up", null, NavigationHotkeys.actionFor(KeyEvent.VK_UP, KeyEvent.ALT_DOWN_MASK));
        assertEqual("alt F5", null, NavigationHotkeys.actionFor(KeyEvent.VK_F5, KeyEvent.ALT_DOWN_MASK));
    }

    private static void testDispatchFromEditorComponent() {
        JFrame frame = new JFrame();
        JTextPane editor = new JTextPane();
        frame.add(editor);
        List<NavigationHotkeys.Action> ran = new ArrayList<>();
        KeyEvent event = keyPressed(editor, KeyEvent.VK_LEFT, KeyEvent.ALT_MASK);
        assertTrue("alt-left dispatched", NavigationHotkeys.dispatch(event, frame, ran::add));
        assertEqual("action", List.of(NavigationHotkeys.Action.BACK), ran);
        assertTrue("consumed", event.isConsumed());
        frame.dispose();
    }

    private static void testDispatchIgnoresOtherWindows() {
        JFrame frame = new JFrame();
        JFrame other = new JFrame();
        JTextPane editor = new JTextPane();
        other.add(editor);
        List<NavigationHotkeys.Action> ran = new ArrayList<>();
        KeyEvent event = keyPressed(editor, KeyEvent.VK_RIGHT, KeyEvent.ALT_MASK);
        assertTrue("other window ignored", !NavigationHotkeys.dispatch(event, frame, ran::add));
        assertEqual("no action", List.of(), ran);
        assertTrue("not consumed", !event.isConsumed());
        frame.dispose();
        other.dispose();
    }

    private static void testDispatchIgnoresKeyRelease() {
        JFrame frame = new JFrame();
        JTextPane editor = new JTextPane();
        frame.add(editor);
        List<NavigationHotkeys.Action> ran = new ArrayList<>();
        KeyEvent event = new KeyEvent(
                editor,
                KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(),
                KeyEvent.ALT_MASK,
                KeyEvent.VK_LEFT,
                KeyEvent.CHAR_UNDEFINED);
        assertTrue("release ignored", !NavigationHotkeys.dispatch(event, frame, ran::add));
        assertEqual("no action", List.of(), ran);
        frame.dispose();
    }

    private static KeyEvent keyPressed(JTextPane editor, int keyCode, int modifiers) {
        return new KeyEvent(
                editor,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                modifiers,
                keyCode,
                KeyEvent.CHAR_UNDEFINED);
    }

    private static void assertEqual(String label, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
            return;
        }
        failed++;
        System.err.println("FAIL " + label + ": expected " + expected + " but was " + actual);
    }

    private static void assertTrue(String label, boolean condition) {
        if (condition) {
            passed++;
            return;
        }
        failed++;
        System.err.println("FAIL " + label);
    }

    private NavigationHotkeysTest() {
    }
}
