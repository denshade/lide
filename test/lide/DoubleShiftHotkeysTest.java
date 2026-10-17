package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void testDoubleShiftOpens() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testDoubleShiftOpens);
            return;
        }
        JFrame frame = new JFrame();
        JTextPane editor = new JTextPane();
        frame.add(editor);
        DoubleShiftHotkeys hotkeys = new DoubleShiftHotkeys();
        List<String> opened = new ArrayList<>();
        long t = 1_000;
        KeyEvent first = shift(editor, KeyEvent.KEY_PRESSED, t);
        assertTrue(!hotkeys.dispatch(first, frame, () -> opened.add("go")), "first press waits");
        assertTrue(!first.isConsumed(), "first not consumed");
        hotkeys.dispatch(shift(editor, KeyEvent.KEY_RELEASED, t + 50), frame, () -> opened.add("go"));
        KeyEvent second = shift(editor, KeyEvent.KEY_PRESSED, t + 120);
        assertTrue(hotkeys.dispatch(second, frame, () -> opened.add("go")), "second press opens");
        assertEquals(List.of("go"), opened, "opened once");
        assertTrue(second.isConsumed(), "second consumed");
        frame.dispose();
    }

    @Test
    void testRequiresReleaseBetweenPresses() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testRequiresReleaseBetweenPresses);
            return;
        }
        JFrame frame = new JFrame();
        JTextPane editor = new JTextPane();
        frame.add(editor);
        DoubleShiftHotkeys hotkeys = new DoubleShiftHotkeys();
        List<String> opened = new ArrayList<>();
        long t = 1_000;
        hotkeys.dispatch(shift(editor, KeyEvent.KEY_PRESSED, t), frame, () -> opened.add("go"));
        KeyEvent repeat = shift(editor, KeyEvent.KEY_PRESSED, t + 30);
        assertTrue(!hotkeys.dispatch(repeat, frame, () -> opened.add("go")), "hold repeat ignored");
        assertEquals(List.of(), opened, "not opened");
        frame.dispose();
    }

    @Test
    void testTimeoutDoesNotOpen() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testTimeoutDoesNotOpen);
            return;
        }
        JFrame frame = new JFrame();
        JTextPane editor = new JTextPane();
        frame.add(editor);
        DoubleShiftHotkeys hotkeys = new DoubleShiftHotkeys();
        List<String> opened = new ArrayList<>();
        long t = 1_000;
        hotkeys.dispatch(shift(editor, KeyEvent.KEY_PRESSED, t), frame, () -> opened.add("go"));
        hotkeys.dispatch(shift(editor, KeyEvent.KEY_RELEASED, t + 10), frame, () -> opened.add("go"));
        KeyEvent late = shift(editor, KeyEvent.KEY_PRESSED, t + DoubleShiftHotkeys.TIMEOUT_MS + 50);
        assertTrue(!hotkeys.dispatch(late, frame, () -> opened.add("go")), "late second is a new first tap");
        assertEquals(List.of(), opened, "not opened");
        frame.dispose();
    }

    @Test
    void testOtherKeyResets() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testOtherKeyResets);
            return;
        }
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
        assertTrue(!hotkeys.dispatch(secondShift, frame, () -> opened.add("go")), "shift after letter is not double-shift");
        assertEquals(List.of(), opened, "not opened");
        frame.dispose();
    }

    @Test
    void testIgnoresShiftWithCtrl() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testIgnoresShiftWithCtrl);
            return;
        }
        JFrame frame = new JFrame();
        JTextPane editor = new JTextPane();
        frame.add(editor);
        DoubleShiftHotkeys hotkeys = new DoubleShiftHotkeys();
        List<String> opened = new ArrayList<>();
        KeyEvent event = new KeyEvent(
                editor, KeyEvent.KEY_PRESSED, 1_000,
                InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK,
                KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED);
        assertTrue(!hotkeys.dispatch(event, frame, () -> opened.add("go")), "ctrl-shift ignored");
        assertEquals(List.of(), opened, "not opened");
        frame.dispose();
    }

    @Test
    void testIgnoresOtherWindows() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testIgnoresOtherWindows);
            return;
        }
        JFrame frame = new JFrame();
        JFrame other = new JFrame();
        JTextPane editor = new JTextPane();
        other.add(editor);
        DoubleShiftHotkeys hotkeys = new DoubleShiftHotkeys();
        List<String> opened = new ArrayList<>();
        long t = 1_000;
        hotkeys.dispatch(shift(editor, KeyEvent.KEY_PRESSED, t), frame, () -> opened.add("go"));
        hotkeys.dispatch(shift(editor, KeyEvent.KEY_RELEASED, t + 10), frame, () -> opened.add("go"));
        assertTrue(!hotkeys.dispatch(shift(editor, KeyEvent.KEY_PRESSED, t + 40), frame, () -> opened.add("go")), "other window ignored");
        assertEquals(List.of(), opened, "not opened");
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

}
