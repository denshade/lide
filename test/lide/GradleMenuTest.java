package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void requireDisplay() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "headless environment cannot run GradleMenuTest");
    }

    @Test
    void testMenuItems() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testMenuItems);
            return;
        }
        MainFrame frame = new MainFrame();
        try {
            JMenu gradle = findMenu(frame.getJMenuBar(), "Gradle");
            assertTrue(gradle != null, "Gradle menu exists");
            List<String> labels = itemLabels(gradle);
            assertEquals(5, labels.size(), "item count");
            assertEquals("Build", labels.get(0), "build");
            assertEquals("Test", labels.get(1), "test");
            assertEquals("Release", labels.get(2), "release");
            assertEquals("Download Dependencies", labels.get(3), "deps");
            assertEquals("Clear", labels.get(4), "clear");
            assertTrue(findMenuItem(gradle, "Build").getAccelerator() == null, "build has no accelerator");
            assertTrue(findMenuItem(gradle, "Test").getAccelerator() == null, "test has no accelerator");
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
            frame.updateGradleMenu();
            JMenu gradle = findMenu(frame.getJMenuBar(), "Gradle");
            for (JMenuItem item : menuItems(gradle)) {
                assertTrue(!item.isEnabled(), item.getText() + " disabled");
            }
        } finally {
            frame.dispose();
        }
    }

    @Test
    void testItemsEnabledForGradleProject() throws Exception {
        Path root = Files.createTempDirectory("lide-gradle-menu");
        Files.writeString(root.resolve("build.gradle"), "plugins { id 'java' }\n");
        try {
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    JMenu gradle = findMenu(frame.getJMenuBar(), "Gradle");
                    for (JMenuItem item : menuItems(gradle)) {
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
        Path root = Files.createTempDirectory("lide-plain-gradle");
        try {
            Files.writeString(root.resolve("readme.txt"), "no gradle");
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    JMenu gradle = findMenu(frame.getJMenuBar(), "Gradle");
                    for (JMenuItem item : menuItems(gradle)) {
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
