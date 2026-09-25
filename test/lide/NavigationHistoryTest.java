package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for in-session file navigation history.
 */
public final class NavigationHistoryTest {

    private static Path path(String name) {
        return Path.of("C:/projects/" + name).toAbsolutePath().normalize();
    }

    @Test
    void testVisitRecordsAndIgnoresConsecutiveDuplicates() throws Exception {
        NavigationHistory history = new NavigationHistory();
        Path a = path("a.txt");
        Path b = path("b.txt");
        history.visit(a);
        history.visit(a);
        history.visit(b);
        history.visit(b);
        assertEquals(List.of(a, b), history.entries(), "entries");
        assertEquals(b, history.current(), "current");
        assertEquals(1, history.index(), "index");
        assertTrue(history.canGoBack(), "can go back");
        assertTrue(!history.canGoForward(), "cannot go forward");
    }

    @Test
    void testBackAndForward() throws Exception {
        NavigationHistory history = new NavigationHistory();
        Path a = path("a.txt");
        Path b = path("b.txt");
        Path c = path("c.txt");
        history.visit(a);
        history.visit(b);
        history.visit(c);
        assertEquals(b, history.back(), "back to b");
        assertEquals(b, history.current(), "current after back");
        assertTrue(history.canGoBack(), "can go back from b");
        assertTrue(history.canGoForward(), "can go forward from b");
        assertEquals(a, history.back(), "back to a");
        assertTrue(!history.canGoBack(), "cannot go back from a");
        assertEquals(b, history.forward(), "forward to b");
        assertEquals(c, history.forward(), "forward to c");
        assertTrue(!history.canGoForward(), "cannot go forward from c");
    }

    @Test
    void testVisitAfterBackDropsForward() throws Exception {
        NavigationHistory history = new NavigationHistory();
        Path a = path("a.txt");
        Path b = path("b.txt");
        Path c = path("c.txt");
        history.visit(a);
        history.visit(b);
        history.back();
        history.visit(c);
        assertEquals(List.of(a, c), history.entries(), "forward dropped");
        assertEquals(c, history.current(), "current");
        assertTrue(!history.canGoForward(), "cannot go forward");
        assertEquals(a, history.back(), "back to a");
    }

    @Test
    void testRespectsLimit() throws Exception {
        NavigationHistory history = new NavigationHistory(2);
        Path a = path("a.txt");
        Path b = path("b.txt");
        Path c = path("c.txt");
        history.visit(a);
        history.visit(b);
        history.visit(c);
        assertEquals(List.of(b, c), history.entries(), "limit");
        assertEquals(1, history.index(), "index");
        assertEquals(b, history.back(), "back");
        assertTrue(!history.canGoBack(), "cannot go further back");
    }

    @Test
    void testRemapRenamedFile() throws Exception {
        NavigationHistory history = new NavigationHistory();
        Path a = path("a.txt");
        Path b = path("b.txt");
        Path renamed = path("renamed.txt");
        history.visit(a);
        history.visit(b);
        history.remap(a, renamed);
        assertEquals(List.of(renamed, b), history.entries(), "remapped");
        assertEquals(b, history.current(), "current still b");
        assertEquals(renamed, history.back(), "back to renamed");
    }

    @Test
    void testBackAtStartIsNoOp() throws Exception {
        NavigationHistory history = new NavigationHistory();
        assertTrue(!history.canGoBack(), "empty cannot go back");
        assertTrue(!history.canGoForward(), "empty cannot go forward");
        assertEquals(null, history.back(), "back empty");
        assertEquals(null, history.forward(), "forward empty");
        history.visit(path("a.txt"));
        assertTrue(!history.canGoBack(), "single cannot go back");
        assertEquals(null, history.back(), "back single");
        assertEquals(path("a.txt"), history.current(), "still at a");
    }

}
