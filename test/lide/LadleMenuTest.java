package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Tests for the Ladle menu on the main window.
 */
public final class LadleMenuTest {

    @BeforeEach
    void requireDisplay() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "headless environment cannot run LadleMenuTest");
    }

    @Test
    void testMenuItems() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testMenuItems);
            return;
        }
        MainFrame frame = new MainFrame();
        try {
            JMenu ladle = findMenu(frame.getJMenuBar(), "Ladle");
            assertTrue(ladle != null, "Ladle menu exists");
            List<String> labels = itemLabels(ladle);
            assertEquals(6, labels.size(), "item count");
            assertEquals("Install Ladle", labels.get(0), "install");
            assertEquals("Build", labels.get(1), "build");
            assertEquals("Test", labels.get(2), "test");
            assertEquals("Release", labels.get(3), "release");
            assertEquals("Download Dependencies", labels.get(4), "deps");
            assertEquals("Clear", labels.get(5), "clear");
            assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), findMenuItem(ladle, "Download Dependencies").getAccelerator(), "download accelerator");
            assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), findMenuItem(ladle, "Build").getAccelerator(), "build accelerator");
            assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), findMenuItem(ladle, "Test").getAccelerator(), "test accelerator");
        } finally {
            frame.dispose();
        }
    }

    @Test
    void testItemsDisabledWithoutProject() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testItemsDisabledWithoutProject);
            return;
        }
        MainFrame frame = new MainFrame();
        try {
            frame.updateLadleMenu();
            JMenu ladle = findMenu(frame.getJMenuBar(), "Ladle");
            for (JMenuItem item : menuItems(ladle)) {
                assertTrue(!item.isEnabled(), item.getText() + " disabled");
            }
        } finally {
            frame.dispose();
        }
    }

    @Test
    void testItemsEnabledForLadleProject() throws Exception {
        Path root = Files.createTempDirectory("lide-ladle-menu");
        Files.createDirectories(root.resolve("lib"));
        Files.writeString(root.resolve("lib").resolve("ladle.jar"), "fake-jar");
        Files.writeString(root.resolve("build.ini"), "[javac]\npath = $JAVA_HOME\n");
        try {
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    JMenu ladle = findMenu(frame.getJMenuBar(), "Ladle");
                    for (JMenuItem item : menuItems(ladle)) {
                        assertTrue(item.isEnabled(), item.getText() + " enabled");
                    }
                } finally {
                    frame.dispose();
                }
            });
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testItemsEnabledForPlainProject() throws Exception {
        Path root = Files.createTempDirectory("lide-plain");
        try {
            Files.writeString(root.resolve("readme.txt"), "no ladle");
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    JMenu ladle = findMenu(frame.getJMenuBar(), "Ladle");
                    for (JMenuItem item : menuItems(ladle)) {
                        assertTrue(item.isEnabled(), item.getText() + " enabled when project is open");
                    }
                } finally {
                    frame.dispose();
                }
            });
        } finally {
            deleteRecursive(root);
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

    private static JMenuItem findMenuItem(JMenu menu, String text) {
        for (JMenuItem item : menuItems(menu)) {
            if (text.equals(item.getText())) {
                return item;
            }
        }
        return null;
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
