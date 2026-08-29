package lide;

import java.awt.GraphicsEnvironment;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Tests for the Navigate menu and editor file history.
 */
public final class NavigateMenuTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: headless environment cannot run Navigate menu tests");
            return;
        }
        SwingUtilities.invokeAndWait(NavigateMenuTest::testMenuItems);
        testBackAndForwardBetweenFiles();
        testForwardClearedWhenOpeningAnotherFile();
        testBackReopensClosedFile();
        testSwitchingOpenTabRecordsHistory();
        testBackSkipsDeletedFile();
        testBackFollowsRenamedFile();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testMenuItems() {
        MainFrame frame = new MainFrame();
        try {
            JMenu navigate = findMenu(frame.getJMenuBar(), "Navigate");
            assertTrue("Navigate menu exists", navigate != null);
            List<String> labels = itemLabels(navigate);
            assertEqual("item count", 2, labels.size());
            assertEqual("back", "Back", labels.get(0));
            assertEqual("forward", "Forward", labels.get(1));
            assertEqual("back accelerator",
                    KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK),
                    findMenuItem(navigate, "Back").getAccelerator());
            assertEqual("forward accelerator",
                    KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK),
                    findMenuItem(navigate, "Forward").getAccelerator());
            frame.updateNavigateMenu();
            assertTrue("back disabled initially", !findMenuItem(navigate, "Back").isEnabled());
            assertTrue("forward disabled initially", !findMenuItem(navigate, "Forward").isEnabled());
        } finally {
            frame.dispose();
        }
    }

    private static void testBackAndForwardBetweenFiles() throws Exception {
        Path dir = Files.createTempDirectory("lide-nav-history");
        try {
            Path a = dir.resolve("a.txt");
            Path b = dir.resolve("b.txt");
            Files.writeString(a, "aaa");
            Files.writeString(b, "bbb");
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    EditorTabPane pane = frame.editorTabs();
                    pane.openFile(a);
                    pane.openFile(b);
                    assertEqual("active is b", normalize(b), pane.getActiveFilePath());
                    assertTrue("can go back", pane.canNavigateBack());
                    assertTrue("cannot go forward", !pane.canNavigateForward());
                    JMenu navigate = findMenu(frame.getJMenuBar(), "Navigate");
                    assertTrue("back enabled", findMenuItem(navigate, "Back").isEnabled());
                    assertTrue("forward disabled", !findMenuItem(navigate, "Forward").isEnabled());

                    findMenuItem(navigate, "Back").doClick();
                    assertEqual("active is a", normalize(a), pane.getActiveFilePath());
                    assertTrue("can go forward", pane.canNavigateForward());
                    assertTrue("forward enabled after back",
                            findMenuItem(navigate, "Forward").isEnabled());

                    findMenuItem(navigate, "Forward").doClick();
                    assertEqual("active is b again", normalize(b), pane.getActiveFilePath());
                } finally {
                    frame.dispose();
                }
            });
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testForwardClearedWhenOpeningAnotherFile() throws Exception {
        Path dir = Files.createTempDirectory("lide-nav-truncate");
        try {
            Path a = dir.resolve("a.txt");
            Path b = dir.resolve("b.txt");
            Path c = dir.resolve("c.txt");
            Files.writeString(a, "aaa");
            Files.writeString(b, "bbb");
            Files.writeString(c, "ccc");
            SwingUtilities.invokeAndWait(() -> {
                EditorTabPane pane = new EditorTabPane();
                pane.openFile(a);
                pane.openFile(b);
                pane.navigateBack();
                pane.openFile(c);
                assertEqual("active is c", normalize(c), pane.getActiveFilePath());
                assertTrue("cannot go forward after new visit", !pane.canNavigateForward());
                assertTrue("went back to a", pane.navigateBack());
                assertEqual("active is a", normalize(a), pane.getActiveFilePath());
            });
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testBackReopensClosedFile() throws Exception {
        Path dir = Files.createTempDirectory("lide-nav-reopen");
        try {
            Path a = dir.resolve("a.txt");
            Path b = dir.resolve("b.txt");
            Files.writeString(a, "aaa");
            Files.writeString(b, "bbb");
            SwingUtilities.invokeAndWait(() -> {
                EditorTabPane pane = new EditorTabPane();
                pane.openFile(a);
                pane.openFile(b);
                pane.closeAllTabs();
                assertEqual("no tabs", 0, pane.getTabCount());
                assertTrue("can go back after close all", pane.canNavigateBack());
                assertTrue("went back", pane.navigateBack());
                assertEqual("reopened previous file", normalize(a), pane.getActiveFilePath());
            });
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testSwitchingOpenTabRecordsHistory() throws Exception {
        Path dir = Files.createTempDirectory("lide-nav-switch");
        try {
            Path a = dir.resolve("a.txt");
            Path b = dir.resolve("b.txt");
            Files.writeString(a, "aaa");
            Files.writeString(b, "bbb");
            SwingUtilities.invokeAndWait(() -> {
                EditorTabPane pane = new EditorTabPane();
                pane.openFile(a);
                pane.openFile(b);
                pane.openFile(a);
                assertEqual("active is a", normalize(a), pane.getActiveFilePath());
                assertTrue("went back", pane.navigateBack());
                assertEqual("previous tab was b", normalize(b), pane.getActiveFilePath());
            });
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testBackSkipsDeletedFile() throws Exception {
        Path dir = Files.createTempDirectory("lide-nav-skip");
        try {
            Path a = dir.resolve("a.txt");
            Path b = dir.resolve("b.txt");
            Path c = dir.resolve("c.txt");
            Files.writeString(a, "aaa");
            Files.writeString(b, "bbb");
            Files.writeString(c, "ccc");
            SwingUtilities.invokeAndWait(() -> {
                EditorTabPane pane = new EditorTabPane();
                pane.openFile(a);
                pane.openFile(b);
                pane.openFile(c);
                JPopupMenu menu = pane.createTabContextMenu(1);
                for (java.awt.Component component : menu.getComponents()) {
                    if (component instanceof JMenuItem item && "Close".equals(item.getText())) {
                        item.doClick();
                        break;
                    }
                }
                try {
                    Files.delete(b);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                assertTrue("went back", pane.navigateBack());
                assertEqual("skipped deleted b", normalize(a), pane.getActiveFilePath());
            });
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testBackFollowsRenamedFile() throws Exception {
        Path dir = Files.createTempDirectory("lide-nav-rename");
        try {
            Path a = dir.resolve("a.txt");
            Path b = dir.resolve("b.txt");
            Files.writeString(a, "aaa");
            Files.writeString(b, "bbb");
            Path renamed = dir.resolve("renamed.txt");
            SwingUtilities.invokeAndWait(() -> {
                EditorTabPane pane = new EditorTabPane();
                pane.openFile(a);
                pane.openFile(b);
                pane.retargetOpenFiles(normalize(a), normalize(renamed));
                assertTrue("went back", pane.navigateBack());
                assertEqual("back to renamed", normalize(renamed), pane.getActiveFilePath());
            });
        } finally {
            deleteRecursive(dir);
        }
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
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
        for (JMenuItem item : menuItems(menu)) {
            if (text.equals(item.getText())) {
                return item;
            }
        }
        return null;
    }

    private static List<JMenuItem> menuItems(JMenu menu) {
        List<JMenuItem> items = new ArrayList<>();
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private static List<String> itemLabels(JMenu menu) {
        List<String> labels = new ArrayList<>();
        for (JMenuItem item : menuItems(menu)) {
            labels.add(item.getText());
        }
        return labels;
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

    private NavigateMenuTest() {
    }
}
