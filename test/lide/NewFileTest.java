package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Assumptions;
import java.awt.GraphicsEnvironment;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Tests for creating a new file from the File menu and project tree.
 */
public final class NewFileTest {

    @Test
    void testCreatesEmptyFile() throws Exception {
        Path dir = Files.createTempDirectory("lide-new-file");
        try {
            Path created = NewFile.create(dir, "Hello.java");
            assertTrue(Files.isRegularFile(created), "exists");
            assertEquals("Hello.java", created.getFileName().toString(), "name");
            assertEquals(0L, Files.size(created), "empty");
            assertEquals(dir.toAbsolutePath().normalize(), created.getParent(), "parent");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testCreatesNestedPath() throws Exception {
        Path dir = Files.createTempDirectory("lide-new-nested");
        try {
            Path created = NewFile.create(dir, "src/pkg/Hello.java");
            assertTrue(Files.isRegularFile(created), "exists");
            assertTrue(Files.isDirectory(dir.resolve("src").resolve("pkg")), "parent dirs");
            assertEquals(Path.of("src", "pkg", "Hello.java"), dir.toAbsolutePath().normalize().relativize(created), "relative");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testRejectsBlankName() throws Exception {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> NewFile.resolve(Path.of("."), "   "));
        assertTrue(ex.getMessage().toLowerCase().contains("file name"), "blank message");
    }

    @Test
    void testRejectsDotDot() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> NewFile.resolve(Path.of("."), "../secret.txt"));
    }

    @Test
    void testRejectsExistingFile() throws Exception {
        Path dir = Files.createTempDirectory("lide-new-exists");
        try {
            Files.writeString(dir.resolve("a.txt"), "keep");
            java.nio.file.FileAlreadyExistsException ex = assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> NewFile.create(dir, "a.txt"));
        assertEquals("keep", Files.readString(dir.resolve("a.txt")), "unchanged");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testRejectsAbsolutePath() throws Exception {
        Path absolute = Path.of("").toAbsolutePath().resolve("outside.txt");
        assertThrows(IllegalArgumentException.class, () -> NewFile.resolve(Path.of("."), absolute.toString()));
    }

    @Test
    void testRejectsTrailingSeparator() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> NewFile.resolve(Path.of("."), "src/"));
    }

    @Test
    void testRejectsInvalidCharacters() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> NewFile.resolve(Path.of("."), "a<b.txt"));
    }

    @Test
    void testFileMenuItem() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testFileMenuItem);
            return;
        }
        MainFrame frame = new MainFrame();
        try {
            JMenu file = findMenu(frame.getJMenuBar(), "File");
            assertTrue(file != null, "File menu exists");
            JMenuItem item = findMenuItem(file, "New File…");
            assertTrue(item != null, "New File item exists");
            assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), item.getAccelerator(), "accelerator");
            List<String> labels = itemLabels(file);
            assertEquals("New File…", labels.get(0), "first item");
        } finally {
            frame.dispose();
        }
    }

    @Test
    void testSelectedDirectoryWithoutProject() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testSelectedDirectoryWithoutProject);
            return;
        }
        ProjectTreePanel tree = new ProjectTreePanel();
        assertTrue(tree.getSelectedDirectory() == null, "no project");
    }

    @Test
    void testCreatesAndOpensInEditor() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        Path root = Files.createTempDirectory("lide-new-ui");
        try {
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    Path created = frame.createNewFileIn(root, "App.java");
                    assertTrue(created != null, "created path");
                    assertTrue(Files.isRegularFile(root.resolve("App.java")), "on disk");
                    CodeEditor editor = frame.editorTabs().getActiveEditor();
                    assertTrue(editor != null, "editor open");
                    assertEquals(root.resolve("App.java").toAbsolutePath().normalize(), editor.getFilePath().toAbsolutePath().normalize(), "editor file");
                    assertEquals("", normalize(editor.getDocumentText()), "empty buffer");
                } finally {
                    frame.dispose();
                }
            });
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testSelectedDirectoryIsProjectRoot() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        Path root = Files.createTempDirectory("lide-new-sel");
        try {
            SwingUtilities.invokeAndWait(() -> {
                ProjectTreePanel tree = new ProjectTreePanel();
                tree.openDirectory(root);
                assertEquals(root.toAbsolutePath().normalize(), tree.getSelectedDirectory().toAbsolutePath().normalize(), "selected dir is root");
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

    private static JMenuItem findMenuItem(JMenu menu, String text) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item != null && text.equals(item.getText())) {
                return item;
            }
        }
        return null;
    }

    private static List<String> itemLabels(JMenu menu) {
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item != null) {
                labels.add(item.getText());
            }
        }
        return labels;
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
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
