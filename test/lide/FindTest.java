package lide;

import java.awt.GraphicsEnvironment;
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
        testWrapAround();
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: headless environment cannot run editor find tests");
        } else {
            SwingUtilities.invokeAndWait(FindTest::testEditorFindBar);
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
