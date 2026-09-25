package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

/**
 * Tests for the Java menu on the main window.
 */
public final class JavaHomeMenuTest {

    private Path previousStorage;
    private Path storage;

    @BeforeEach
    void useTempJavaHomeStorage() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "headless environment cannot run Java menu tests");
        previousStorage = JavaHome.defaultStorageFile();
        storage = Files.createTempFile("lide-javahome-menu-", ".txt");
        Files.deleteIfExists(storage);
        JavaHome.useStorageFile(storage);
    }

    @AfterEach
    void restoreJavaHomeStorage() throws Exception {
        if (storage == null) {
            return;
        }
        JavaHome.clear();
        JavaHome.useStorageFile(previousStorage);
        Files.deleteIfExists(storage);
    }

    @Test
    void testMenuItemsWhenUnset() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testMenuItemsWhenUnset);
            return;
        }
        MainFrame frame = new MainFrame();
        try {
            JMenu java = findMenu(frame.getJMenuBar(), "Java");
            assertTrue(java != null, "Java menu exists");
            frame.updateJavaHomeMenu();
            List<String> labels = itemLabels(java);
            assertEquals(3, labels.size(), "item count");
            assertEquals("JAVA_HOME: (not set)", labels.get(0), "current");
            assertEquals("Set JAVA_HOME…", labels.get(1), "set");
            assertEquals("Clear JAVA_HOME", labels.get(2), "clear");
            assertTrue(!findMenuItem(java, "JAVA_HOME: (not set)").isEnabled(), "current disabled");
            assertTrue(findMenuItem(java, "Set JAVA_HOME…").isEnabled(), "set enabled");
            assertTrue(!findMenuItem(java, "Clear JAVA_HOME").isEnabled(), "clear disabled when unset");
        } finally {
            frame.dispose();
        }
    }

    @Test
    void testClearEnabledAfterSet() throws Exception {
        Path jdk = Files.createTempDirectory("lide-javahome-menu-jdk");
        try {
            Files.createDirectories(jdk.resolve("bin"));
            String javac = ScriptCommand.isWindows() ? "javac.exe" : "javac";
            Files.writeString(jdk.resolve("bin").resolve(javac), "");
            JavaHome.set(jdk);
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.updateJavaHomeMenu();
                    JMenu java = findMenu(frame.getJMenuBar(), "Java");
                    List<String> labels = itemLabels(java);
                    String expected = "JAVA_HOME: " + jdk.toAbsolutePath().normalize();
                    assertEquals(expected, labels.get(0), "current path");
                    assertTrue(!findMenuItem(java, expected).isEnabled(), "current still disabled");
                    assertTrue(findMenuItem(java, "Clear JAVA_HOME").isEnabled(), "clear enabled");
                } finally {
                    frame.dispose();
                }
            });
        } finally {
            deleteRecursive(jdk);
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
