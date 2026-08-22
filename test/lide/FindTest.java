package lide;

import java.awt.GraphicsEnvironment;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Tests for find-in-file search.
 */
public final class FindTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testFindNext();
        testFindPrevious();
        testMatchCase();
        testCountMatches();
        testLineHelpers();
        testWrapAround();
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: headless environment cannot run editor find tests");
        } else {
            SwingUtilities.invokeAndWait(() -> {
                testEditorFindBar();
                testFindHighlightDoesNotStealFocus();
                testFindWorksWithCrlfFile();
            });
        }
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testFindNext() {
        String text = "one two one";
        assertEqual("first", 0, TextFinder.findNext(text, "one", 0, true));
        assertEqual("second", 8, TextFinder.findNext(text, "one", 1, true));
        assertEqual("missing", -1, TextFinder.findNext(text, "three", 0, true));
    }

    private static void testFindPrevious() {
        String text = "one two one";
        assertEqual("prev last", 8, TextFinder.findPrevious(text, "one", 11, true));
        assertEqual("prev first", 0, TextFinder.findPrevious(text, "one", 8, true));
    }

    private static void testMatchCase() {
        String text = "Alpha alpha";
        assertEqual("case sensitive", 6, TextFinder.findNext(text, "alpha", 0, true));
        assertEqual("case insensitive", 0, TextFinder.findNext(text, "alpha", 0, false));
    }

    private static void testCountMatches() {
        assertEqual("count", 3, TextFinder.countMatches("aaa", "a", true));
        assertEqual("count word", 2, TextFinder.countMatches("one two one", "one", true));
        assertEqual("empty query", 0, TextFinder.countMatches("abc", "", true));
        assertEqual("findAll size", 2, TextFinder.findAll("one two one", "one", true).size());
        assertEqual("findAll first", 0, TextFinder.findAll("one two one", "one", true).get(0));
        assertEqual("findAll second", 8, TextFinder.findAll("one two one", "one", true).get(1));
    }

    private static void testLineHelpers() {
        assertEqual("lf only", "a\nb", TextFinder.normalizeNewlines("a\nb"));
        assertEqual("crlf", "a\nb", TextFinder.normalizeNewlines("a\r\nb"));
        assertEqual("cr", "a\nb", TextFinder.normalizeNewlines("a\rb"));
        String text = "alpha\nbeta\ngamma";
        assertEqual("line 1", 1, TextFinder.lineNumber(text, 0));
        assertEqual("line 2", 2, TextFinder.lineNumber(text, 6));
        assertEqual("line 3", 3, TextFinder.lineNumber(text, 11));
        assertEqual("lineAt 1", "alpha", TextFinder.lineAt(text, 0));
        assertEqual("lineAt 2", "beta", TextFinder.lineAt(text, 6));
        assertEqual("lineAt 3", "gamma", TextFinder.lineAt(text, 11));
    }

    private static void testWrapAround() {
        String text = "abc def abc";
        assertEqual("next wrap", 0, TextFinder.findNext(text, "abc", 9, true));
        assertEqual("prev wrap", 8, TextFinder.findPrevious(text, "abc", 0, true));
    }

    private static void testEditorFindBar() {
        EditorTabPane pane = new EditorTabPane();
        pane.openUntitled("f.txt", "foo bar foo", Language.PLAIN);
        assertTrue("find hidden", !pane.isFindVisible());
        pane.showFind();
        assertTrue("find visible", pane.isFindVisible());

        // Drive find via public next/previous after setting query through selection.
        CodeEditor editor = pane.getActiveEditor();
        editor.selectRange(0, 3);
        pane.hideFind();
        pane.showFind();
        assertTrue("find next", pane.findNext());
        assertEqual("second foo selected", "foo", editor.getSelectedText());
        assertEqual("selection at second", 8, editor.getSelectionStart());

        assertTrue("find previous", pane.findPrevious());
        assertEqual("back to first", 0, editor.getSelectionStart());

        pane.hideFind();
        assertTrue("find hidden again", !pane.isFindVisible());
    }

    private static void testFindHighlightDoesNotStealFocus() {
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

            assertEqual("matched", "foo", pane.getActiveEditor().getSelectedText());
            assertTrue(
                    "editor must not steal focus on find highlight",
                    !pane.getActiveEditor().getTextPane().isFocusOwner());
            assertTrue(
                    "selection stays visible without editor focus",
                    pane.getActiveEditor().getTextPane().getCaret().isSelectionVisible());
            if (otherHadFocus) {
                assertTrue("external focus retained after highlight", other.isFocusOwner());
            }
        } finally {
            frame.dispose();
        }
    }

    private static void testFindWorksWithCrlfFile() {
        JFrame frame = new JFrame("Find CRLF test");
        try {
            EditorTabPane pane = new EditorTabPane();
            frame.add(pane);
            frame.setSize(640, 480);
            frame.setVisible(true);
            // Content with CR so JTextPane.getText() diverges from document offsets.
            pane.openUntitled("crlf.txt", "alpha\r\nbeta\r\nalpha", Language.PLAIN);
            CodeEditor editor = pane.getActiveEditor();
            assertTrue("doc is LF", !editor.getDocumentText().contains("\r"));
            pane.showFind();
            pane.findBar().setQuery("alpha");
            assertEqual("first match selected", "alpha", editor.getSelectedText());
            assertEqual("first match at start", 0, editor.getSelectionStart());
            assertTrue("find next", pane.findNext());
            assertEqual("second match selected", "alpha", editor.getSelectedText());
            assertEqual(
                    "second match index",
                    editor.getDocumentText().lastIndexOf("alpha"),
                    editor.getSelectionStart());
        } finally {
            frame.dispose();
        }
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

    private FindTest() {
    }
}
