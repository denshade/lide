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
 * Tests for application-wide Navigate hotkeys.
 */
public final class NavigationHotkeysTest {

    @Test
    void testActionMapping() throws Exception {
        assertEquals(NavigationHotkeys.Action.BACK, NavigationHotkeys.actionFor(KeyEvent.VK_LEFT, KeyEvent.ALT_DOWN_MASK), "alt left");
        assertEquals(NavigationHotkeys.Action.FORWARD, NavigationHotkeys.actionFor(KeyEvent.VK_RIGHT, KeyEvent.ALT_DOWN_MASK), "alt right");
        assertEquals(NavigationHotkeys.Action.TO_TEST, NavigationHotkeys.actionFor(KeyEvent.VK_T,
                        KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "ctrl shift T");
        assertEquals(NavigationHotkeys.Action.TO_IMPLEMENTATION, NavigationHotkeys.actionFor(KeyEvent.VK_I,
                        KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "ctrl shift I");
    }

    @Test
    void testIgnoresWrongModifiers() throws Exception {
        assertEquals(null, NavigationHotkeys.actionFor(KeyEvent.VK_LEFT, 0), "plain left");
        assertEquals(null, NavigationHotkeys.actionFor(KeyEvent.VK_LEFT,
                        KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK), "ctrl alt left");
        assertEquals(null, NavigationHotkeys.actionFor(KeyEvent.VK_LEFT,
                        KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "ctrl shift T");
        assertEquals(null, NavigationHotkeys.actionFor(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK), "ctrl T");
    }

    @Test
    void testIgnoresUnknownKeys() throws Exception {
        assertEquals(null, NavigationHotkeys.actionFor(KeyEvent.VK_UP, KeyEvent.ALT_DOWN_MASK), "alt up");
        assertEquals(null, NavigationHotkeys.actionFor(KeyEvent.VK_F5, KeyEvent.ALT_DOWN_MASK), "alt F5");
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
        List<NavigationHotkeys.Action> ran = new ArrayList<>();
        KeyEvent event = keyPressed(editor, KeyEvent.VK_LEFT, KeyEvent.ALT_MASK);
        assertTrue(NavigationHotkeys.dispatch(event, frame, ran::add), "alt-left dispatched");
        assertEquals(List.of(NavigationHotkeys.Action.BACK), ran, "action");
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
        List<NavigationHotkeys.Action> ran = new ArrayList<>();
        KeyEvent event = keyPressed(editor, KeyEvent.VK_RIGHT, KeyEvent.ALT_MASK);
        assertTrue(!NavigationHotkeys.dispatch(event, frame, ran::add), "other window ignored");
        assertEquals(List.of(), ran, "no action");
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
        List<NavigationHotkeys.Action> ran = new ArrayList<>();
        KeyEvent event = new KeyEvent(
                editor,
                KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(),
                KeyEvent.ALT_MASK,
                KeyEvent.VK_LEFT,
                KeyEvent.CHAR_UNDEFINED);
        assertTrue(!NavigationHotkeys.dispatch(event, frame, ran::add), "release ignored");
        assertEquals(List.of(), ran, "no action");
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

}
