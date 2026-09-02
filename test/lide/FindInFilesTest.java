package lide;

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
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testFindsMatchesAcrossFiles();
        testSkipsBuildDirAndBinary();
        testMatchCaseAndLineNumbers();
        testCrlfOffsetsMatchEditor();
        testTruncatesAtMaxMatches();
        testEmptyQuery();
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: headless environment cannot run find-in-files UI tests");
        } else {
            SwingUtilities.invokeAndWait(() -> {
                testPanelStartsMinimized();
                testPanelMinimizeRestore();
                testPanelSearchAndOpen();
                testMenuItemAndShowExpandsPanel();
                testOpenMatchSelectsText();
            });
        }
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testFindsMatchesAcrossFiles() throws Exception {
        Path root = Files.createTempDirectory("lide-find-all");
        try {
            Files.writeString(root.resolve("a.txt"), "hello world\n");
            Path nested = root.resolve("src");
            Files.createDirectories(nested);
            Files.writeString(nested.resolve("b.java"), "class Hello {}\n");
            Files.writeString(root.resolve("miss.txt"), "nothing here\n");

            ProjectFinder.Result result = ProjectFinder.search(root, "hello", false);
            assertEqual("match count", 2, result.size());
            assertTrue("searched files", result.filesSearched() >= 3);
            assertTrue("has a.txt", result.matches().stream()
                    .anyMatch(m -> m.relativePath().equals("a.txt")));
            assertTrue("has b.java", result.matches().stream()
                    .anyMatch(m -> m.relativePath().equals("src/b.java")));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testSkipsBuildDirAndBinary() throws Exception {
        Path root = Files.createTempDirectory("lide-find-skip");
        try {
            Files.writeString(root.resolve("keep.txt"), "needle\n");
            Path out = root.resolve("out");
            Files.createDirectories(out);
            Files.writeString(out.resolve("skip.txt"), "needle\n");
            Files.write(root.resolve("binary.txt"), new byte[] {'n', 'e', 0, 'e', 'd', 'l', 'e'});
            Files.write(root.resolve("app.jar"), "needle".getBytes(StandardCharsets.UTF_8));

            ProjectFinder.Result result = ProjectFinder.search(root, "needle", true);
            assertEqual("only keep.txt", 1, result.size());
            assertEqual("path", "keep.txt", result.matches().get(0).relativePath());
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testMatchCaseAndLineNumbers() throws Exception {
        Path root = Files.createTempDirectory("lide-find-case");
        try {
            Files.writeString(root.resolve("c.txt"), "Alpha\nbeta\nalpha\n");
            ProjectFinder.Result insensitive = ProjectFinder.search(root, "alpha", false);
            assertEqual("insensitive", 2, insensitive.size());
            ProjectFinder.Result sensitive = ProjectFinder.search(root, "alpha", true);
            assertEqual("sensitive", 1, sensitive.size());
            assertEqual("line", 3, sensitive.matches().get(0).lineNumber());
            assertTrue("snippet", sensitive.matches().get(0).snippet().contains("alpha"));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testCrlfOffsetsMatchEditor() throws Exception {
        Path root = Files.createTempDirectory("lide-find-crlf");
        try {
            Files.writeString(root.resolve("crlf.txt"), "alpha\r\nbeta\r\nalpha");
            ProjectFinder.Result result = ProjectFinder.search(root, "alpha", true);
            assertEqual("two matches", 2, result.size());
            assertEqual("first offset", 0, result.matches().get(0).offset());
            String normalized = TextFinder.normalizeNewlines("alpha\r\nbeta\r\nalpha");
            assertEqual("second offset", normalized.lastIndexOf("alpha"), result.matches().get(1).offset());
            assertEqual("second line", 3, result.matches().get(1).lineNumber());
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testTruncatesAtMaxMatches() throws Exception {
        Path root = Files.createTempDirectory("lide-find-max");
        try {
            Files.writeString(root.resolve("many.txt"), "x x x x x\n");
            ProjectFinder.Result result = ProjectFinder.search(
                    root, "x", true, 3, 100, ProjectFinder.MAX_FILE_BYTES, () -> false);
            assertEqual("capped", 3, result.size());
            assertTrue("truncated", result.truncated());
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testEmptyQuery() throws Exception {
        Path root = Files.createTempDirectory("lide-find-empty");
        try {
            Files.writeString(root.resolve("a.txt"), "hello\n");
            ProjectFinder.Result result = ProjectFinder.search(root, "", false);
            assertEqual("empty query", 0, result.size());
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testPanelStartsMinimized() {
        FindInFilesPanel panel = new FindInFilesPanel();
        assertTrue("starts minimized", panel.isMinimized());
        assertTrue("body hidden", !panel.isBodyVisible());
        assertTrue("collapsed height",
                panel.getPreferredSize().height < FindInFilesPanel.EXPANDED_HEIGHT);
    }

    private static void testPanelMinimizeRestore() {
        FindInFilesPanel panel = new FindInFilesPanel();
        panel.setMinimized(false);
        assertTrue("expanded", !panel.isMinimized());
        assertTrue("body shown", panel.isBodyVisible());
        assertTrue("expanded height", panel.getPreferredSize().height >= FindInFilesPanel.EXPANDED_HEIGHT);
        assertEqual("minimize tooltip", "Minimize", panel.minimizeButton().getToolTipText());

        panel.minimizeButton().doClick();
        assertTrue("minimized by button", panel.isMinimized());
        assertTrue("body hidden again", !panel.isBodyVisible());
        assertEqual("restore tooltip", "Restore", panel.minimizeButton().getToolTipText());

        panel.minimizeButton().doClick();
        assertTrue("restored by button", !panel.isMinimized());
        assertTrue("body shown again", panel.isBodyVisible());
    }

    private static void testPanelSearchAndOpen() {
        try {
            Path root = Files.createTempDirectory("lide-find-panel");
            Files.writeString(root.resolve("hit.txt"), "find-me please\n");
            FindInFilesPanel panel = new FindInFilesPanel();
            AtomicReference<ProjectFinder.Match> opened = new AtomicReference<>();
            panel.setOpenMatchHandler(opened::set);
            panel.setProjectRoot(root);
            panel.setQuery("find-me");
            panel.searchNow();
            assertEqual("result count", 1, panel.getResultCount());
            assertTrue("status mentions match", panel.getStatus().contains("1 match"));
            panel.selectResult(0);
            panel.openSelected();
            assertTrue("opened match", opened.get() != null);
            assertEqual("opened file", "hit.txt", opened.get().relativePath());
            deleteRecursiveQuiet(root);
        } catch (Exception ex) {
            failed++;
            System.err.println("FAIL panel search: " + ex.getMessage());
        }
    }

    private static void testMenuItemAndShowExpandsPanel() {
        MainFrame frame = new MainFrame();
        try {
            JMenu edit = findMenu(frame.getJMenuBar(), "Edit");
            assertTrue("Edit menu", edit != null);
            JMenuItem item = findMenuItem(edit, "Find in Files…");
            assertTrue("Find in Files item", item != null);
            assertTrue("starts minimized", frame.findInFilesPanel().isMinimized());
            frame.showFindInFiles();
            assertTrue("show expands", !frame.findInFilesPanel().isMinimized());
            assertTrue("body visible", frame.findInFilesPanel().isBodyVisible());
        } finally {
            frame.dispose();
        }
    }

    private static void testOpenMatchSelectsText() {
        try {
            Path dir = Files.createTempDirectory("lide-open-match");
            Path file = dir.resolve("src.txt");
            Files.writeString(file, "aaa bbb aaa");
            EditorTabPane pane = new EditorTabPane();
            pane.openMatch(file, 8, 3);
            CodeEditor editor = pane.getActiveEditor();
            assertTrue("editor open", editor != null);
            assertEqual("selected", "aaa", editor.getSelectedText());
            assertEqual("start", 8, editor.getSelectionStart());
            deleteRecursiveQuiet(dir);
        } catch (Exception ex) {
            failed++;
            System.err.println("FAIL open match: " + ex.getMessage());
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

    private FindInFilesTest() {
    }
}
