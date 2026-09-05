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
 * Tests for the Gradle menu on the main window.
 */
public final class GradleMenuTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: headless environment cannot run Gradle menu tests");
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            testMenuItems();
            testItemsDisabledWithoutProject();
        });
        testItemsEnabledForGradleProject();
        testItemsEnabledForPlainProject();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testMenuItems() {
        MainFrame frame = new MainFrame();
        try {
            JMenu gradle = findMenu(frame.getJMenuBar(), "Gradle");
            assertTrue("Gradle menu exists", gradle != null);
            List<String> labels = itemLabels(gradle);
            assertEqual("item count", 5, labels.size());
            assertEqual("build", "Build", labels.get(0));
            assertEqual("test", "Test", labels.get(1));
            assertEqual("release", "Release", labels.get(2));
            assertEqual("deps", "Download Dependencies", labels.get(3));
            assertEqual("clear", "Clear", labels.get(4));
            assertTrue("build has no accelerator",
                    findMenuItem(gradle, "Build").getAccelerator() == null);
            assertTrue("test has no accelerator",
                    findMenuItem(gradle, "Test").getAccelerator() == null);
        } finally {
            frame.dispose();
        }
    }

    private static void testItemsDisabledWithoutProject() {
        MainFrame frame = new MainFrame();
        try {
            frame.updateGradleMenu();
            JMenu gradle = findMenu(frame.getJMenuBar(), "Gradle");
            for (JMenuItem item : menuItems(gradle)) {
                assertTrue(item.getText() + " disabled", !item.isEnabled());
            }
        } finally {
            frame.dispose();
        }
    }

    private static void testItemsEnabledForGradleProject() throws Exception {
        Path root = Files.createTempDirectory("lide-gradle-menu");
        Files.writeString(root.resolve("build.gradle"), "plugins { id 'java' }\n");
        try {
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    JMenu gradle = findMenu(frame.getJMenuBar(), "Gradle");
                    for (JMenuItem item : menuItems(gradle)) {
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
        Path root = Files.createTempDirectory("lide-plain-gradle");
        try {
            Files.writeString(root.resolve("readme.txt"), "no gradle");
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    JMenu gradle = findMenu(frame.getJMenuBar(), "Gradle");
                    for (JMenuItem item : menuItems(gradle)) {
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

    private GradleMenuTest() {
    }
}
