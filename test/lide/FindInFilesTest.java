package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

/**
 * Tests for find-in-files search and the results panel.
 */
public final class FindInFilesTest {

    @Test
    void testFindsMatchesAcrossFiles() throws Exception {
        Path root = Files.createTempDirectory("lide-find-all");
        try {
            Files.writeString(root.resolve("a.txt"), "hello world\n");
            Path nested = root.resolve("src");
            Files.createDirectories(nested);
            Files.writeString(nested.resolve("b.java"), "class Hello {}\n");
            Files.writeString(root.resolve("miss.txt"), "nothing here\n");

            ProjectFinder.Result result = ProjectFinder.search(root, "hello", false);
            assertEquals(2, result.size(), "match count");
            assertTrue(result.filesSearched() >= 3, "searched files");
            assertTrue(result.matches().stream()
                    .anyMatch(m -> m.relativePath().equals("a.txt")), "has a.txt");
            assertTrue(result.matches().stream()
                    .anyMatch(m -> m.relativePath().equals("src/b.java")), "has b.java");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testSkipsBuildDirAndBinary() throws Exception {
        Path root = Files.createTempDirectory("lide-find-skip");
        try {
            Files.writeString(root.resolve("keep.txt"), "needle\n");
            Path out = root.resolve("out");
            Files.createDirectories(out);
            Files.writeString(out.resolve("skip.txt"), "needle\n");
            Files.write(root.resolve("binary.txt"), new byte[] {'n', 'e', 0, 'e', 'd', 'l', 'e'});
            Files.write(root.resolve("app.jar"), "needle".getBytes(StandardCharsets.UTF_8));

            ProjectFinder.Result result = ProjectFinder.search(root, "needle", true);
            assertEquals(1, result.size(), "only keep.txt");
            assertEquals("keep.txt", result.matches().get(0).relativePath(), "path");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testMatchCaseAndLineNumbers() throws Exception {
        Path root = Files.createTempDirectory("lide-find-case");
        try {
            Files.writeString(root.resolve("c.txt"), "Alpha\nbeta\nalpha\n");
            ProjectFinder.Result insensitive = ProjectFinder.search(root, "alpha", false);
            assertEquals(2, insensitive.size(), "insensitive");
            ProjectFinder.Result sensitive = ProjectFinder.search(root, "alpha", true);
            assertEquals(1, sensitive.size(), "sensitive");
            assertEquals(3, sensitive.matches().get(0).lineNumber(), "line");
            assertTrue(sensitive.matches().get(0).snippet().contains("alpha"), "snippet");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testCrlfOffsetsMatchEditor() throws Exception {
        Path root = Files.createTempDirectory("lide-find-crlf");
        try {
            Files.writeString(root.resolve("crlf.txt"), "alpha\r\nbeta\r\nalpha");
            ProjectFinder.Result result = ProjectFinder.search(root, "alpha", true);
            assertEquals(2, result.size(), "two matches");
            assertEquals(0, result.matches().get(0).offset(), "first offset");
            String normalized = TextFinder.normalizeNewlines("alpha\r\nbeta\r\nalpha");
            assertEquals(normalized.lastIndexOf("alpha"), result.matches().get(1).offset(), "second offset");
            assertEquals(3, result.matches().get(1).lineNumber(), "second line");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testTruncatesAtMaxMatches() throws Exception {
        Path root = Files.createTempDirectory("lide-find-max");
        try {
            Files.writeString(root.resolve("many.txt"), "x x x x x\n");
            ProjectFinder.Result result = ProjectFinder.search(
                    root, "x", true, 3, 100, ProjectFinder.MAX_FILE_BYTES, () -> false);
            assertEquals(3, result.size(), "capped");
            assertTrue(result.truncated(), "truncated");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testEmptyQuery() throws Exception {
        Path root = Files.createTempDirectory("lide-find-empty");
        try {
            Files.writeString(root.resolve("a.txt"), "hello\n");
            ProjectFinder.Result result = ProjectFinder.search(root, "", false);
            assertEquals(0, result.size(), "empty query");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testPanelStartsMinimized() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testPanelStartsMinimized);
            return;
        }
        FindInFilesPanel panel = new FindInFilesPanel();
        assertTrue(panel.isMinimized(), "starts minimized");
        assertTrue(!panel.isBodyVisible(), "body hidden");
        assertTrue(panel.getPreferredSize().height < FindInFilesPanel.EXPANDED_HEIGHT, "collapsed height");
    }

    @Test
    void testPanelMinimizeRestore() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testPanelMinimizeRestore);
            return;
        }
        FindInFilesPanel panel = new FindInFilesPanel();
        panel.setMinimized(false);
        assertTrue(!panel.isMinimized(), "expanded");
        assertTrue(panel.isBodyVisible(), "body shown");
        assertTrue(panel.getPreferredSize().height >= FindInFilesPanel.EXPANDED_HEIGHT, "expanded height");
        assertEquals("Minimize", panel.minimizeButton().getToolTipText(), "minimize tooltip");

        panel.minimizeButton().doClick();
        assertTrue(panel.isMinimized(), "minimized by button");
        assertTrue(!panel.isBodyVisible(), "body hidden again");
        assertEquals("Restore", panel.minimizeButton().getToolTipText(), "restore tooltip");

        panel.minimizeButton().doClick();
        assertTrue(!panel.isMinimized(), "restored by button");
        assertTrue(panel.isBodyVisible(), "body shown again");
    }

    @Test
    void testPanelSearchAndOpen() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testPanelSearchAndOpen);
            return;
        }
        Path root = Files.createTempDirectory("lide-find-panel");
        try {
            Files.writeString(root.resolve("hit.txt"), "find-me please\n");
            FindInFilesPanel panel = new FindInFilesPanel();
            AtomicReference<ProjectFinder.Match> opened = new AtomicReference<>();
            panel.setOpenMatchHandler(opened::set);
            panel.setProjectRoot(root);
            panel.setQuery("find-me");
            panel.searchNow();
            assertEquals(1, panel.getResultCount(), "result count");
            assertTrue(panel.getStatus().contains("1 match"), "status mentions match");
            panel.selectResult(0);
            panel.openSelected();
            assertTrue(opened.get() != null, "opened match");
            assertEquals("hit.txt", opened.get().relativePath(), "opened file");
        } finally {
            deleteRecursiveQuiet(root);
        }
    }

    @Test
    void testMenuItemAndShowExpandsPanel() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testMenuItemAndShowExpandsPanel);
            return;
        }
        MainFrame frame = new MainFrame();
        try {
            JMenu edit = findMenu(frame.getJMenuBar(), "Edit");
            assertTrue(edit != null, "Edit menu");
            JMenuItem item = findMenuItem(edit, "Find in Files…");
            assertTrue(item != null, "Find in Files item");
            assertTrue(frame.findInFilesPanel().isMinimized(), "starts minimized");
            frame.showFindInFiles();
            assertTrue(!frame.findInFilesPanel().isMinimized(), "show expands");
            assertTrue(frame.findInFilesPanel().isBodyVisible(), "body visible");
        } finally {
            frame.dispose();
        }
    }

    @Test
    void testOpenMatchSelectsText() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testOpenMatchSelectsText);
            return;
        }
        Path dir = Files.createTempDirectory("lide-open-match");
        try {
            Path file = dir.resolve("src.txt");
            Files.writeString(file, "aaa bbb aaa");
            EditorTabPane pane = new EditorTabPane();
            pane.openMatch(file, 8, 3);
            CodeEditor editor = pane.getActiveEditor();
            assertTrue(editor != null, "editor open");
            assertEquals("aaa", editor.getSelectedText(), "selected");
            assertEquals(8, editor.getSelectionStart(), "start");
        } finally {
            deleteRecursiveQuiet(dir);
        }
    }

    private static JMenu findMenu(JMenuBar bar, String text) {
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu != null && text.equals(menu.getText())) {
                return menu;
            }
        }
        return null;
    }

    private static JMenuItem findMenuItem(JMenu menu, String text) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item != null && text.equals(item.getText())) {
                return item;
            }
        }
        return null;
    }

    private static void deleteRecursive(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            List<Path> paths = walk.sorted((a, b) -> b.compareTo(a)).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void deleteRecursiveQuiet(Path root) {
        try {
            deleteRecursive(root);
        } catch (Exception ex) {
            AppLog.exception("Could not delete " + root, ex);
        }
    }

}
