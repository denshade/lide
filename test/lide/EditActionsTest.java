package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import javax.swing.SwingUtilities;

/**
 * Tests for Edit menu actions: undo, redo, copy, paste.
 */
public final class EditActionsTest {

    @BeforeEach
    void requireDisplay() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "headless environment cannot run EditActionsTest");
    }

    @Test
    void testUndoRedo() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testUndoRedo);
            return;
        }
        CodeEditor editor = new CodeEditor();
        editor.setPlainContent("hello", Language.PLAIN);
        assertTrue(!editor.canUndo(), "no undo after load");
        assertTrue(!editor.canRedo(), "no redo after load");

        editor.getTextPane().setCaretPosition(editor.getTextPane().getDocument().getLength());
        editor.getTextPane().replaceSelection("!");
        assertTrue(editor.canUndo(), "can undo after edit");
        assertEquals("hello!", normalize(editor.getText()), "text after edit");

        editor.undo();
        assertEquals("hello", normalize(editor.getText()), "text after undo");
        assertTrue(editor.canRedo(), "can redo after undo");

        editor.redo();
        assertEquals("hello!", normalize(editor.getText()), "text after redo");
    }

    @Test
    void testCopyPaste() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testCopyPaste);
            return;
        }
        CodeEditor editor = new CodeEditor();
        editor.setPlainContent("abc", Language.PLAIN);
        editor.getTextPane().select(0, 3);
        editor.copy();
        editor.getTextPane().setCaretPosition(3);
        editor.paste();
        assertEquals("abcabc", normalize(editor.getText()), "paste after copy");
    }

    @Test
    void testEditActionsViaTabPane() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testEditActionsViaTabPane);
            return;
        }
        EditorTabPane pane = new EditorTabPane();
        pane.openUntitled("t.txt", "xy", Language.PLAIN);
        assertTrue(pane.hasActiveEditor(), "has editor");
        assertTrue(!pane.canUndoActive(), "no undo yet");

        CodeEditor editor = pane.getActiveEditor();
        editor.getTextPane().setCaretPosition(2);
        editor.getTextPane().replaceSelection("z");
        assertTrue(pane.canUndoActive(), "can undo via pane");
        assertTrue(pane.undoActive(), "undo via pane");
        assertEquals("xy", normalize(editor.getText()), "undone via pane");
        assertTrue(pane.canRedoActive(), "can redo via pane");
        assertTrue(pane.redoActive(), "redo via pane");
        assertEquals("xyz", normalize(editor.getText()), "redone via pane");

        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection("Q"), null);
        editor.getTextPane().setCaretPosition(0);
        editor.getTextPane().moveCaretPosition(0);
        assertTrue(pane.pasteActive(), "paste via pane");
        assertTrue(normalize(editor.getText()).startsWith("Q"), "starts with paste");

        editor.getTextPane().select(0, 1);
        assertTrue(pane.copyActive(), "copy via pane");
                    String clip = (String) Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
            assertEquals("Q", clip, "clipboard");
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

}
