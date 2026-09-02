package lide;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

/**
 * Tests for Shift-Shift detection.
 */
public final class DoubleShiftHotkeysTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            testDoubleShiftOpens();
            testRequiresReleaseBetweenPresses();
            testTimeoutDoesNotOpen();
            testOtherKeyResets();
            testIgnoresShiftWithCtrl();
            testIgnoresOtherWindows();
        });
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testDoubleShiftOpens() {
        JFrame frame = new JFrame();
        JTextPane editor = new JTextPane();
        frame.add(editor);
        DoubleShiftHotkeys hotkeys = new DoubleShiftHotkeys();
        List<String> opened = new ArrayList<>();
        long t = 1_000;
        KeyEvent first = shift(editor, KeyEvent.KEY_PRESSED, t);
        assertTrue("first press waits", !hotkeys.dispatch(first, frame, () -> opened.add("go")));
        assertTrue("first not consumed", !first.isConsumed());
        hotkeys.dispatch(shift(editor, KeyEvent.KEY_RELEASED, t + 50), frame, () -> opened.add("go"));
        KeyEvent second = shift(editor, KeyEvent.KEY_PRESSED, t + 120);
        assertTrue("second press opens", hotkeys.dispatch(second, frame, () -> opened.add("go")));
        assertEqual("opened once", List.of("go"), opened);
        assertTrue("second consumed", second.isConsumed());
        frame.dispose();
    }

    private static void testRequiresReleaseBetweenPresses() {
        JFrame frame = new JFrame();
        JTextPane editor = new JTextPane();
        frame.add(editor);
        DoubleShiftHotkeys hotkeys = new DoubleShiftHotkeys();
        List<String> opened = new ArrayList<>();
        long t = 1_000;
        hotkeys.dispatch(shift(editor, KeyEvent.KEY_PRESSED, t), frame, () -> opened.add("go"));
        KeyEvent repeat = shift(editor, KeyEvent.KEY_PRESSED, t + 30);
        assertTrue("hold repeat ignored", !hotkeys.dispatch(repeat, frame, () -> opened.add("go")));
        assertEqual("not opened", List.of(), opened);
        frame.dispose();
    }

    private static void testTimeoutDoesNotOpen() {
        JFrame frame = new JFrame();
        JTextPane editor = new JTextPane();
        frame.add(editor);
        DoubleShiftHotkeys hotkeys = new DoubleShiftHotkeys();
        List<String> opened = new ArrayList<>();
        long t = 1_000;
        hotkeys.dispatch(shift(editor, KeyEvent.KEY_PRESSED, t), frame, () -> opened.add("go"));
        hotkeys.dispatch(shift(editor, KeyEvent.KEY_RELEASED, t + 10), frame, () -> opened.add("go"));
        KeyEvent late = shift(editor, KeyEvent.KEY_PRESSED, t + DoubleShiftHotkeys.TIMEOUT_MS + 50);
        assertTrue("late second is a new first tap", !hotkeys.dispatch(late, frame, () -> opened.add("go")));
        assertEqual("not opened", List.of(), opened);
        frame.dispose();
    }

    private static void testOtherKeyResets() {
        JFrame frame = new JFrame();
        JTextPane editor = new JTextPane();
        frame.add(editor);
        DoubleShiftHotkeys hotkeys = new DoubleShiftHotkeys();
        List<String> opened = new ArrayList<>();
        long t = 1_000;
        hotkeys.dispatch(shift(editor, KeyEvent.KEY_PRESSED, t), frame, () -> opened.add("go"));
        hotkeys.dispatch(shift(editor, KeyEvent.KEY_RELEASED, t + 10), frame, () -> opened.add("go"));
        KeyEvent letter = new KeyEvent(
                editor, KeyEvent.KEY_PRESSED, t + 40, InputEvent.SHIFT_DOWN_MASK,
                KeyEvent.VK_A, 'A');
        hotkeys.dispatch(letter, frame, () -> opened.add("go"));
        KeyEvent secondShift = shift(editor, KeyEvent.KEY_PRESSED, t + 80);
        assertTrue("shift after letter is not double-shift",
                !hotkeys.dispatch(secondShift, frame, () -> opened.add("go")));
        assertEqual("not opened", List.of(), opened);
        frame.dispose();
    }

    private static void testIgnoresShiftWithCtrl() {
        JFrame frame = new JFrame();
        JTextPane editor = new JTextPane();
        frame.add(editor);
        DoubleShiftHotkeys hotkeys = new DoubleShiftHotkeys();
        List<String> opened = new ArrayList<>();
        KeyEvent event = new KeyEvent(
                editor, KeyEvent.KEY_PRESSED, 1_000,
                InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK,
                KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED);
        assertTrue("ctrl-shift ignored", !hotkeys.dispatch(event, frame, () -> opened.add("go")));
        assertEqual("not opened", List.of(), opened);
        frame.dispose();
    }

    private static void testIgnoresOtherWindows() {
        JFrame frame = new JFrame();
        JFrame other = new JFrame();
        JTextPane editor = new JTextPane();
        other.add(editor);
        DoubleShiftHotkeys hotkeys = new DoubleShiftHotkeys();
        List<String> opened = new ArrayList<>();
        long t = 1_000;
        hotkeys.dispatch(shift(editor, KeyEvent.KEY_PRESSED, t), frame, () -> opened.add("go"));
        hotkeys.dispatch(shift(editor, KeyEvent.KEY_RELEASED, t + 10), frame, () -> opened.add("go"));
        assertTrue("other window ignored",
                !hotkeys.dispatch(shift(editor, KeyEvent.KEY_PRESSED, t + 40), frame, () -> opened.add("go")));
        assertEqual("not opened", List.of(), opened);
        frame.dispose();
        other.dispose();
    }

    private static KeyEvent shift(JTextPane editor, int id, long when) {
        return new KeyEvent(
                editor,
                id,
                when,
                InputEvent.SHIFT_DOWN_MASK,
                KeyEvent.VK_SHIFT,
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

    private DoubleShiftHotkeysTest() {
    }
}
