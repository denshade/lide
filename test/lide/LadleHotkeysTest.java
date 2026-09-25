package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void testCommandMapping() throws Exception {
        assertEquals(LadleCommand.DEPENDENCY, LadleHotkeys.commandFor(KeyEvent.VK_F4, 0), "F4");
        assertEquals(LadleCommand.BUILD, LadleHotkeys.commandFor(KeyEvent.VK_F5, 0), "F5");
        assertEquals(LadleCommand.TEST, LadleHotkeys.commandFor(KeyEvent.VK_F6, 0), "F6");
    }

    @Test
    void testIgnoresModifiers() throws Exception {
        assertEquals(null, LadleHotkeys.commandFor(KeyEvent.VK_F5, KeyEvent.SHIFT_DOWN_MASK), "shift F5");
        assertEquals(null, LadleHotkeys.commandFor(KeyEvent.VK_F5, KeyEvent.CTRL_DOWN_MASK), "ctrl F5");
        assertEquals(null, LadleHotkeys.commandFor(KeyEvent.VK_F4, KeyEvent.ALT_DOWN_MASK), "alt F4");
    }

    @Test
    void testIgnoresUnknownKeys() throws Exception {
        assertEquals(null, LadleHotkeys.commandFor(KeyEvent.VK_F3, 0), "F3");
        assertEquals(null, LadleHotkeys.commandFor(KeyEvent.VK_F7, 0), "F7");
    }

    @Test
    void testDispatchFromEditorComponent() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testDispatchFromEditorComponent);
            return;
        }
        JFrame frame = new JFrame();
        JTextPane editor = new JTextPane();
        frame.add(editor);
        List<String> ran = new ArrayList<>();
        KeyEvent event = keyPressed(editor, KeyEvent.VK_F5);
        assertTrue(LadleHotkeys.dispatch(event, frame, ran::add), "F5 dispatched");
        assertEquals(List.of(LadleCommand.BUILD), ran, "command");
        assertTrue(event.isConsumed(), "consumed");
        frame.dispose();
    }

    @Test
    void testDispatchIgnoresOtherWindows() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testDispatchIgnoresOtherWindows);
            return;
        }
        JFrame frame = new JFrame();
        JFrame other = new JFrame();
        JTextPane editor = new JTextPane();
        other.add(editor);
        List<String> ran = new ArrayList<>();
        KeyEvent event = keyPressed(editor, KeyEvent.VK_F6);
        assertTrue(!LadleHotkeys.dispatch(event, frame, ran::add), "other window ignored");
        assertEquals(List.of(), ran, "no command");
        assertTrue(!event.isConsumed(), "not consumed");
        frame.dispose();
        other.dispose();
    }

    @Test
    void testDispatchIgnoresKeyRelease() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testDispatchIgnoresKeyRelease);
            return;
        }
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
        assertTrue(!LadleHotkeys.dispatch(event, frame, ran::add), "release ignored");
        assertEquals(List.of(), ran, "no command");
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

}
