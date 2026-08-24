package lide;

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
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: headless environment cannot run Ladle menu tests");
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            testMenuItems();
            testItemsDisabledWithoutProject();
        });
        testItemsEnabledForLadleProject();
        testItemsEnabledForPlainProject();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testMenuItems() {
        MainFrame frame = new MainFrame();
        try {
            JMenu ladle = findMenu(frame.getJMenuBar(), "Ladle");
            assertTrue("Ladle menu exists", ladle != null);
            List<String> labels = itemLabels(ladle);
            assertEqual("item count", 6, labels.size());
            assertEqual("install", "Install Ladle", labels.get(0));
            assertEqual("build", "Build", labels.get(1));
            assertEqual("test", "Test", labels.get(2));
            assertEqual("release", "Release", labels.get(3));
            assertEqual("deps", "Download Dependencies", labels.get(4));
            assertEqual("clear", "Clear", labels.get(5));
            assertEqual("download accelerator",
                    KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0),
                    findMenuItem(ladle, "Download Dependencies").getAccelerator());
            assertEqual("build accelerator",
                    KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0),
                    findMenuItem(ladle, "Build").getAccelerator());
            assertEqual("test accelerator",
                    KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0),
                    findMenuItem(ladle, "Test").getAccelerator());
        } finally {
            frame.dispose();
        }
    }

    private static void testItemsDisabledWithoutProject() {
        MainFrame frame = new MainFrame();
        try {
            frame.updateLadleMenu();
            JMenu ladle = findMenu(frame.getJMenuBar(), "Ladle");
            for (JMenuItem item : menuItems(ladle)) {
                assertTrue(item.getText() + " disabled", !item.isEnabled());
            }
        } finally {
            frame.dispose();
        }
    }

    private static void testItemsEnabledForLadleProject() throws Exception {
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
                        assertTrue(item.getText() + " enabled", item.isEnabled());
                    }
                } finally {
                    frame.dispose();
                }
            });
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testItemsEnabledForPlainProject() throws Exception {
        Path root = Files.createTempDirectory("lide-plain");
        try {
            Files.writeString(root.resolve("readme.txt"), "no ladle");
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    JMenu ladle = findMenu(frame.getJMenuBar(), "Ladle");
                    for (JMenuItem item : menuItems(ladle)) {
                        assertTrue(item.getText() + " enabled when project is open", item.isEnabled());
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

    private LadleMenuTest() {
    }
}
