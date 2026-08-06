package lide;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import javax.swing.SwingUtilities;

/**
 * Tests for Edit menu actions: undo, redo, copy, paste.
 */
public final class EditActionsTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: headless environment cannot run edit action tests");
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            testUndoRedo();
            testCopyPaste();
            testEditActionsViaTabPane();
        });
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testUndoRedo() {
        CodeEditor editor = new CodeEditor();
        editor.setPlainContent("hello", Language.PLAIN);
        assertTrue("no undo after load", !editor.canUndo());
        assertTrue("no redo after load", !editor.canRedo());

        editor.getTextPane().setCaretPosition(editor.getTextPane().getDocument().getLength());
        editor.getTextPane().replaceSelection("!");
        assertTrue("can undo after edit", editor.canUndo());
        assertEqual("text after edit", "hello!", normalize(editor.getText()));

        editor.undo();
        assertEqual("text after undo", "hello", normalize(editor.getText()));
        assertTrue("can redo after undo", editor.canRedo());

        editor.redo();
        assertEqual("text after redo", "hello!", normalize(editor.getText()));
    }

    private static void testCopyPaste() {
        CodeEditor editor = new CodeEditor();
        editor.setPlainContent("abc", Language.PLAIN);
        editor.getTextPane().select(0, 3);
        editor.copy();
        editor.getTextPane().setCaretPosition(3);
        editor.paste();
        assertEqual("paste after copy", "abcabc", normalize(editor.getText()));
    }

    private static void testEditActionsViaTabPane() {
        EditorTabPane pane = new EditorTabPane();
        pane.openUntitled("t.txt", "xy", Language.PLAIN);
        assertTrue("has editor", pane.hasActiveEditor());
        assertTrue("no undo yet", !pane.canUndoActive());

        CodeEditor editor = pane.getActiveEditor();
        editor.getTextPane().setCaretPosition(2);
        editor.getTextPane().replaceSelection("z");
        assertTrue("can undo via pane", pane.canUndoActive());
        assertTrue("undo via pane", pane.undoActive());
        assertEqual("undone via pane", "xy", normalize(editor.getText()));
        assertTrue("can redo via pane", pane.canRedoActive());
        assertTrue("redo via pane", pane.redoActive());
        assertEqual("redone via pane", "xyz", normalize(editor.getText()));

        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection("Q"), null);
        editor.getTextPane().setCaretPosition(0);
        editor.getTextPane().moveCaretPosition(0);
        assertTrue("paste via pane", pane.pasteActive());
        assertTrue("starts with paste", normalize(editor.getText()).startsWith("Q"));

        editor.getTextPane().select(0, 1);
        assertTrue("copy via pane", pane.copyActive());
        try {
            String clip = (String) Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
            assertEqual("clipboard", "Q", clip);
        } catch (Exception ex) {
            failed++;
            System.err.println("FAIL clipboard read: " + ex.getMessage());
        }
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static void assertEqual(String label, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
            return;
        }
        failed++;
        System.err.println("FAIL " + label + ": expected [" + expected + "] but was [" + actual + "]");
    }

    private static void assertTrue(String label, boolean condition) {
        if (condition) {
            passed++;
            return;
        }
        failed++;
        System.err.println("FAIL " + label);
    }

    private EditActionsTest() {
    }
}
