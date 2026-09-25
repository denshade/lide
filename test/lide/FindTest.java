package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import java.awt.GraphicsEnvironment;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Tests for find-in-file search.
 */
public final class FindTest {

    @Test
    void testFindNext() throws Exception {
        String text = "one two one";
        assertEquals(0, TextFinder.findNext(text, "one", 0, true), "first");
        assertEquals(8, TextFinder.findNext(text, "one", 1, true), "second");
        assertEquals(-1, TextFinder.findNext(text, "three", 0, true), "missing");
    }

    @Test
    void testFindPrevious() throws Exception {
        String text = "one two one";
        assertEquals(8, TextFinder.findPrevious(text, "one", 11, true), "prev last");
        assertEquals(0, TextFinder.findPrevious(text, "one", 8, true), "prev first");
    }

    @Test
    void testMatchCase() throws Exception {
        String text = "Alpha alpha";
        assertEquals(6, TextFinder.findNext(text, "alpha", 0, true), "case sensitive");
        assertEquals(0, TextFinder.findNext(text, "alpha", 0, false), "case insensitive");
    }

    @Test
    void testCountMatches() throws Exception {
        assertEquals(3, TextFinder.countMatches("aaa", "a", true), "count");
        assertEquals(2, TextFinder.countMatches("one two one", "one", true), "count word");
        assertEquals(0, TextFinder.countMatches("abc", "", true), "empty query");
        assertEquals(2, TextFinder.findAll("one two one", "one", true).size(), "findAll size");
        assertEquals(0, TextFinder.findAll("one two one", "one", true).get(0), "findAll first");
        assertEquals(8, TextFinder.findAll("one two one", "one", true).get(1), "findAll second");
    }

    @Test
    void testLineHelpers() throws Exception {
        assertEquals("a\nb", TextFinder.normalizeNewlines("a\nb"), "lf only");
        assertEquals("a\nb", TextFinder.normalizeNewlines("a\r\nb"), "crlf");
        assertEquals("a\nb", TextFinder.normalizeNewlines("a\rb"), "cr");
        String text = "alpha\nbeta\ngamma";
        assertEquals(1, TextFinder.lineNumber(text, 0), "line 1");
        assertEquals(2, TextFinder.lineNumber(text, 6), "line 2");
        assertEquals(3, TextFinder.lineNumber(text, 11), "line 3");
        assertEquals("alpha", TextFinder.lineAt(text, 0), "lineAt 1");
        assertEquals("beta", TextFinder.lineAt(text, 6), "lineAt 2");
        assertEquals("gamma", TextFinder.lineAt(text, 11), "lineAt 3");
    }

    @Test
    void testWrapAround() throws Exception {
        String text = "abc def abc";
        assertEquals(0, TextFinder.findNext(text, "abc", 9, true), "next wrap");
        assertEquals(8, TextFinder.findPrevious(text, "abc", 0, true), "prev wrap");
    }

    @Test
    void testEditorFindBar() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testEditorFindBar);
            return;
        }
        EditorTabPane pane = new EditorTabPane();
        pane.openUntitled("f.txt", "foo bar foo", Language.PLAIN);
        assertTrue(!pane.isFindVisible(), "find hidden");
        pane.showFind();
        assertTrue(pane.isFindVisible(), "find visible");

        // Drive find via public next/previous after setting query through selection.
        CodeEditor editor = pane.getActiveEditor();
        editor.selectRange(0, 3);
        pane.hideFind();
        pane.showFind();
        assertTrue(pane.findNext(), "find next");
        assertEquals("foo", editor.getSelectedText(), "second foo selected");
        assertEquals(8, editor.getSelectionStart(), "selection at second");

        assertTrue(pane.findPrevious(), "find previous");
        assertEquals(0, editor.getSelectionStart(), "back to first");

        pane.hideFind();
        assertTrue(!pane.isFindVisible(), "find hidden again");
    }

    @Test
    void testFindHighlightDoesNotStealFocus() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testFindHighlightDoesNotStealFocus);
            return;
        }
        JFrame frame = new JFrame("Find focus test");
        try {
            javax.swing.JTextField other = new javax.swing.JTextField();
            EditorTabPane pane = new EditorTabPane();
            frame.setLayout(new java.awt.BorderLayout());
            frame.add(other, java.awt.BorderLayout.NORTH);
            frame.add(pane, java.awt.BorderLayout.CENTER);
            frame.setSize(640, 480);
            frame.setVisible(true);

            pane.openUntitled("f.txt", "foo bar foo", Language.PLAIN);
            pane.showFind();

            other.requestFocusInWindow();
            boolean otherHadFocus = other.isFocusOwner();

            // Typing in the find box triggers this path and must not move focus to the editor.
            pane.findBar().setQuery("foo");

            assertEquals("foo", pane.getActiveEditor().getSelectedText(), "matched");
            assertTrue(!pane.getActiveEditor().getTextPane().isFocusOwner(), "editor must not steal focus on find highlight");
            assertTrue(pane.getActiveEditor().getTextPane().getCaret().isSelectionVisible(), "selection stays visible without editor focus");
            if (otherHadFocus) {
                assertTrue(other.isFocusOwner(), "external focus retained after highlight");
            }
        } finally {
            frame.dispose();
        }
    }

    @Test
    void testFindWorksWithCrlfFile() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testFindWorksWithCrlfFile);
            return;
        }
        JFrame frame = new JFrame("Find CRLF test");
        try {
            EditorTabPane pane = new EditorTabPane();
            frame.add(pane);
            frame.setSize(640, 480);
            frame.setVisible(true);
            // Content with CR so JTextPane.getText() diverges from document offsets.
            pane.openUntitled("crlf.txt", "alpha\r\nbeta\r\nalpha", Language.PLAIN);
            CodeEditor editor = pane.getActiveEditor();
            assertTrue(!editor.getDocumentText().contains("\r"), "doc is LF");
            pane.showFind();
            pane.findBar().setQuery("alpha");
            assertEquals("alpha", editor.getSelectedText(), "first match selected");
            assertEquals(0, editor.getSelectionStart(), "first match at start");
            assertTrue(pane.findNext(), "find next");
            assertEquals("alpha", editor.getSelectedText(), "second match selected");
            assertEquals(editor.getDocumentText().lastIndexOf("alpha"), editor.getSelectionStart(), "second match index");
        } finally {
            frame.dispose();
        }
    }

}
