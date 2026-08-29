package lide;

import java.nio.file.Path;
import java.util.List;

/**
 * Tests for in-session file navigation history.
 */
public final class NavigationHistoryTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        testVisitRecordsAndIgnoresConsecutiveDuplicates();
        testBackAndForward();
        testVisitAfterBackDropsForward();
        testRespectsLimit();
        testRemapRenamedFile();
        testBackAtStartIsNoOp();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static Path path(String name) {
        return Path.of("C:/projects/" + name).toAbsolutePath().normalize();
    }

    private static void testVisitRecordsAndIgnoresConsecutiveDuplicates() {
        NavigationHistory history = new NavigationHistory();
        Path a = path("a.txt");
        Path b = path("b.txt");
        history.visit(a);
        history.visit(a);
        history.visit(b);
        history.visit(b);
        assertEqual("entries", List.of(a, b), history.entries());
        assertEqual("current", b, history.current());
        assertEqual("index", 1, history.index());
        assertTrue("can go back", history.canGoBack());
        assertTrue("cannot go forward", !history.canGoForward());
    }

    private static void testBackAndForward() {
        NavigationHistory history = new NavigationHistory();
        Path a = path("a.txt");
        Path b = path("b.txt");
        Path c = path("c.txt");
        history.visit(a);
        history.visit(b);
        history.visit(c);
        assertEqual("back to b", b, history.back());
        assertEqual("current after back", b, history.current());
        assertTrue("can go back from b", history.canGoBack());
        assertTrue("can go forward from b", history.canGoForward());
        assertEqual("back to a", a, history.back());
        assertTrue("cannot go back from a", !history.canGoBack());
        assertEqual("forward to b", b, history.forward());
        assertEqual("forward to c", c, history.forward());
        assertTrue("cannot go forward from c", !history.canGoForward());
    }

    private static void testVisitAfterBackDropsForward() {
        NavigationHistory history = new NavigationHistory();
        Path a = path("a.txt");
        Path b = path("b.txt");
        Path c = path("c.txt");
        history.visit(a);
        history.visit(b);
        history.back();
        history.visit(c);
        assertEqual("forward dropped", List.of(a, c), history.entries());
        assertEqual("current", c, history.current());
        assertTrue("cannot go forward", !history.canGoForward());
        assertEqual("back to a", a, history.back());
    }

    private static void testRespectsLimit() {
        NavigationHistory history = new NavigationHistory(2);
        Path a = path("a.txt");
        Path b = path("b.txt");
        Path c = path("c.txt");
        history.visit(a);
        history.visit(b);
        history.visit(c);
        assertEqual("limit", List.of(b, c), history.entries());
        assertEqual("index", 1, history.index());
        assertEqual("back", b, history.back());
        assertTrue("cannot go further back", !history.canGoBack());
    }

    private static void testRemapRenamedFile() {
        NavigationHistory history = new NavigationHistory();
        Path a = path("a.txt");
        Path b = path("b.txt");
        Path renamed = path("renamed.txt");
        history.visit(a);
        history.visit(b);
        history.remap(a, renamed);
        assertEqual("remapped", List.of(renamed, b), history.entries());
        assertEqual("current still b", b, history.current());
        assertEqual("back to renamed", renamed, history.back());
    }

    private static void testBackAtStartIsNoOp() {
        NavigationHistory history = new NavigationHistory();
        assertTrue("empty cannot go back", !history.canGoBack());
        assertTrue("empty cannot go forward", !history.canGoForward());
        assertEqual("back empty", null, history.back());
        assertEqual("forward empty", null, history.forward());
        history.visit(path("a.txt"));
        assertTrue("single cannot go back", !history.canGoBack());
        assertEqual("back single", null, history.back());
        assertEqual("still at a", path("a.txt"), history.current());
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

    private NavigationHistoryTest() {
    }
}
