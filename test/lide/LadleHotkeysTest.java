package lide;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

/**
 * Tests for application-wide Ladle hotkeys.
 */
public final class LadleHotkeysTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testCommandMapping();
        testIgnoresModifiers();
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

    private static void testCommandMapping() {
        assertEqual("F4", LadleCommand.DEPENDENCY, LadleHotkeys.commandFor(KeyEvent.VK_F4, 0));
        assertEqual("F5", LadleCommand.BUILD, LadleHotkeys.commandFor(KeyEvent.VK_F5, 0));
        assertEqual("F6", LadleCommand.TEST, LadleHotkeys.commandFor(KeyEvent.VK_F6, 0));
    }

    private static void testIgnoresModifiers() {
        assertEqual("shift F5", null, LadleHotkeys.commandFor(KeyEvent.VK_F5, KeyEvent.SHIFT_DOWN_MASK));
        assertEqual("ctrl F5", null, LadleHotkeys.commandFor(KeyEvent.VK_F5, KeyEvent.CTRL_DOWN_MASK));
        assertEqual("alt F4", null, LadleHotkeys.commandFor(KeyEvent.VK_F4, KeyEvent.ALT_DOWN_MASK));
    }

    private static void testIgnoresUnknownKeys() {
        assertEqual("F3", null, LadleHotkeys.commandFor(KeyEvent.VK_F3, 0));
        assertEqual("F7", null, LadleHotkeys.commandFor(KeyEvent.VK_F7, 0));
    }

    private static void testDispatchFromEditorComponent() {
        JFrame frame = new JFrame();
        JTextPane editor = new JTextPane();
        frame.add(editor);
        List<String> ran = new ArrayList<>();
        KeyEvent event = keyPressed(editor, KeyEvent.VK_F5);
        assertTrue("F5 dispatched", LadleHotkeys.dispatch(event, frame, ran::add));
        assertEqual("command", List.of(LadleCommand.BUILD), ran);
        assertTrue("consumed", event.isConsumed());
        frame.dispose();
    }

    private static void testDispatchIgnoresOtherWindows() {
        JFrame frame = new JFrame();
        JFrame other = new JFrame();
        JTextPane editor = new JTextPane();
        other.add(editor);
        List<String> ran = new ArrayList<>();
        KeyEvent event = keyPressed(editor, KeyEvent.VK_F6);
        assertTrue("other window ignored", !LadleHotkeys.dispatch(event, frame, ran::add));
        assertEqual("no command", List.of(), ran);
        assertTrue("not consumed", !event.isConsumed());
        frame.dispose();
        other.dispose();
    }

    private static void testDispatchIgnoresKeyRelease() {
        JFrame frame = new JFrame();
        JTextPane editor = new JTextPane();
        frame.add(editor);
        List<String> ran = new ArrayList<>();
        KeyEvent event = new KeyEvent(
                editor,
                KeyEvent.KEY_RELEASED,
                0L,
                0,
                KeyEvent.VK_F4,
                KeyEvent.CHAR_UNDEFINED);
        assertTrue("release ignored", !LadleHotkeys.dispatch(event, frame, ran::add));
        assertEqual("no command", List.of(), ran);
        frame.dispose();
    }

    private static KeyEvent keyPressed(JTextPane editor, int keyCode) {
        return new KeyEvent(
                editor,
                KeyEvent.KEY_PRESSED,
                0L,
                0,
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

    private LadleHotkeysTest() {
    }
}
