package lide;

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
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: headless environment cannot run Java menu tests");
            return;
        }
        Path previousStorage = JavaHome.defaultStorageFile();
        Path storage = Files.createTempFile("lide-javahome-menu-", ".txt");
        try {
            Files.deleteIfExists(storage);
            JavaHome.useStorageFile(storage);
            SwingUtilities.invokeAndWait(JavaHomeMenuTest::testMenuItemsWhenUnset);
            testClearEnabledAfterSet();
        } finally {
            JavaHome.clear();
            JavaHome.useStorageFile(previousStorage);
            Files.deleteIfExists(storage);
        }
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testMenuItemsWhenUnset() {
        MainFrame frame = new MainFrame();
        try {
            JMenu java = findMenu(frame.getJMenuBar(), "Java");
            assertTrue("Java menu exists", java != null);
            frame.updateJavaHomeMenu();
            List<String> labels = itemLabels(java);
            assertEqual("item count", 3, labels.size());
            assertEqual("current", "JAVA_HOME: (not set)", labels.get(0));
            assertEqual("set", "Set JAVA_HOME…", labels.get(1));
            assertEqual("clear", "Clear JAVA_HOME", labels.get(2));
            assertTrue("current disabled", !findMenuItem(java, "JAVA_HOME: (not set)").isEnabled());
            assertTrue("set enabled", findMenuItem(java, "Set JAVA_HOME…").isEnabled());
            assertTrue("clear disabled when unset", !findMenuItem(java, "Clear JAVA_HOME").isEnabled());
        } finally {
            frame.dispose();
        }
    }

    private static void testClearEnabledAfterSet() throws Exception {
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
                    assertEqual("current path", expected, labels.get(0));
                    assertTrue("current still disabled", !findMenuItem(java, expected).isEnabled());
                    assertTrue("clear enabled", findMenuItem(java, "Clear JAVA_HOME").isEnabled());
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

    private JavaHomeMenuTest() {
    }
}
