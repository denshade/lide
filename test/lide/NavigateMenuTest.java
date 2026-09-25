package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void requireDisplay() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "headless environment cannot run NavigateMenuTest");
    }

    @Test
    void testMenuItems() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testMenuItems);
            return;
        }
        MainFrame frame = new MainFrame();
        try {
            JMenu navigate = findMenu(frame.getJMenuBar(), "Navigate");
            assertTrue(navigate != null, "Navigate menu exists");
            List<String> labels = itemLabels(navigate);
            assertEquals(5, labels.size(), "item count");
            assertEquals("Back", labels.get(0), "back");
            assertEquals("Forward", labels.get(1), "forward");
            assertEquals("Go to Class…", labels.get(2), "go to class");
            assertEquals("To Test", labels.get(3), "to test");
            assertEquals("To Implementation", labels.get(4), "to implementation");
            assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), findMenuItem(navigate, "Back").getAccelerator(), "back accelerator");
            assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), findMenuItem(navigate, "Forward").getAccelerator(), "forward accelerator");
            assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_T,
                            InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), findMenuItem(navigate, "To Test").getAccelerator(), "to test accelerator");
            assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_I,
                            InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), findMenuItem(navigate, "To Implementation").getAccelerator(), "to implementation accelerator");
            frame.updateNavigateMenu();
            assertTrue(!findMenuItem(navigate, "Back").isEnabled(), "back disabled initially");
            assertTrue(!findMenuItem(navigate, "Forward").isEnabled(), "forward disabled initially");
            assertTrue(!findMenuItem(navigate, "To Test").isEnabled(), "to test disabled initially");
            assertTrue(!findMenuItem(navigate, "To Implementation").isEnabled(), "to implementation disabled initially");
            assertTrue(!findMenuItem(navigate, "Go to Class…").isEnabled(), "go to class disabled without project");
        } finally {
            frame.dispose();
        }
    }

    @Test
    void testBackAndForwardBetweenFiles() throws Exception {
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
                    assertEquals(normalize(b), pane.getActiveFilePath(), "active is b");
                    assertTrue(pane.canNavigateBack(), "can go back");
                    assertTrue(!pane.canNavigateForward(), "cannot go forward");
                    JMenu navigate = findMenu(frame.getJMenuBar(), "Navigate");
                    assertTrue(findMenuItem(navigate, "Back").isEnabled(), "back enabled");
                    assertTrue(!findMenuItem(navigate, "Forward").isEnabled(), "forward disabled");

                    findMenuItem(navigate, "Back").doClick();
                    assertEquals(normalize(a), pane.getActiveFilePath(), "active is a");
                    assertTrue(pane.canNavigateForward(), "can go forward");
                    assertTrue(findMenuItem(navigate, "Forward").isEnabled(), "forward enabled after back");

                    findMenuItem(navigate, "Forward").doClick();
                    assertEquals(normalize(b), pane.getActiveFilePath(), "active is b again");
                } finally {
                    frame.dispose();
                }
            });
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testForwardClearedWhenOpeningAnotherFile() throws Exception {
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
                assertEquals(normalize(c), pane.getActiveFilePath(), "active is c");
                assertTrue(!pane.canNavigateForward(), "cannot go forward after new visit");
                assertTrue(pane.navigateBack(), "went back to a");
                assertEquals(normalize(a), pane.getActiveFilePath(), "active is a");
            });
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testBackReopensClosedFile() throws Exception {
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
                assertEquals(0, pane.getTabCount(), "no tabs");
                assertTrue(pane.canNavigateBack(), "can go back after close all");
                assertTrue(pane.navigateBack(), "went back");
                assertEquals(normalize(a), pane.getActiveFilePath(), "reopened previous file");
            });
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testSwitchingOpenTabRecordsHistory() throws Exception {
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
                assertEquals(normalize(a), pane.getActiveFilePath(), "active is a");
                assertTrue(pane.navigateBack(), "went back");
                assertEquals(normalize(b), pane.getActiveFilePath(), "previous tab was b");
            });
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testBackSkipsDeletedFile() throws Exception {
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
                assertTrue(pane.navigateBack(), "went back");
                assertEquals(normalize(a), pane.getActiveFilePath(), "skipped deleted b");
            });
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testBackFollowsRenamedFile() throws Exception {
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
                assertTrue(pane.navigateBack(), "went back");
                assertEquals(normalize(renamed), pane.getActiveFilePath(), "back to renamed");
            });
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testToTestAndToImplementation() throws Exception {
        Path root = Files.createTempDirectory("lide-nav-toggle");
        try {
            Path src = root.resolve("src").resolve("lide");
            Path test = root.resolve("test").resolve("lide");
            Files.createDirectories(src);
            Files.createDirectories(test);
            Path impl = src.resolve("Foo.java");
            Path testFile = test.resolve("FooTest.java");
            Files.writeString(impl, "class Foo {}");
            Files.writeString(testFile, "class FooTest {}");
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    EditorTabPane pane = frame.editorTabs();
                    pane.openFile(impl);
                    frame.updateNavigateMenu();
                    JMenu navigate = findMenu(frame.getJMenuBar(), "Navigate");
                    assertTrue(findMenuItem(navigate, "To Test").isEnabled(), "to test enabled on class");
                    assertTrue(!findMenuItem(navigate, "To Implementation").isEnabled(), "to impl disabled on class");
                    findMenuItem(navigate, "To Test").doClick();
                    assertEquals(normalize(testFile), pane.getActiveFilePath(), "opened test");
                    assertTrue(!findMenuItem(navigate, "To Test").isEnabled(), "to test disabled on test");
                    assertTrue(findMenuItem(navigate, "To Implementation").isEnabled(), "to impl enabled on test");
                    findMenuItem(navigate, "To Implementation").doClick();
                    assertEquals(normalize(impl), pane.getActiveFilePath(), "opened impl");
                } finally {
                    frame.dispose();
                }
            });
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testToTestDisabledWhenMissing() throws Exception {
        Path root = Files.createTempDirectory("lide-nav-no-test");
        try {
            Path src = root.resolve("src");
            Files.createDirectories(src);
            Path impl = src.resolve("Lonely.java");
            Files.writeString(impl, "class Lonely {}");
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    frame.editorTabs().openFile(impl);
                    frame.updateNavigateMenu();
                    JMenu navigate = findMenu(frame.getJMenuBar(), "Navigate");
                    assertTrue(!findMenuItem(navigate, "To Test").isEnabled(), "to test disabled when missing");
                    assertTrue(!findMenuItem(navigate, "To Implementation").isEnabled(), "to impl disabled on source");
                } finally {
                    frame.dispose();
                }
            });
        } finally {
            deleteRecursive(root);
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

}
