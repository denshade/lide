package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Assumptions;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * Tests for renaming files and copying their paths.
 */
public final class FileRenameTest {

    @Test
    void testRenamesFile() throws Exception {
        Path dir = Files.createTempDirectory("lide-rename-file");
        try {
            Path source = dir.resolve("old.txt");
            Files.writeString(source, "hello");
            Path renamed = FileRename.rename(source, "new.txt");
            assertTrue(!Files.exists(source), "old gone");
            assertTrue(Files.isRegularFile(renamed), "new exists");
            assertEquals("new.txt", renamed.getFileName().toString(), "name");
            assertEquals("hello", Files.readString(renamed), "content");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testRenamesDirectory() throws Exception {
        Path dir = Files.createTempDirectory("lide-rename-dir");
        try {
            Path source = Files.createDirectory(dir.resolve("pkg"));
            Files.writeString(source.resolve("A.java"), "class A {}");
            Path renamed = FileRename.rename(source, "other");
            assertTrue(!Files.exists(source), "old gone");
            assertTrue(Files.isDirectory(renamed), "dir moved");
            assertTrue(Files.isRegularFile(renamed.resolve("A.java")), "child moved");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testRejectsExistingTarget() throws Exception {
        Path dir = Files.createTempDirectory("lide-rename-exists");
        try {
            Path source = dir.resolve("a.txt");
            Files.writeString(source, "a");
            Files.writeString(dir.resolve("b.txt"), "b");
            java.nio.file.FileAlreadyExistsException ex = assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> FileRename.rename(source, "b.txt"));
        assertEquals("a", Files.readString(source), "source kept");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testRejectsBlankAndSeparators() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> FileRename.validateSimpleName("  "));
        assertThrows(IllegalArgumentException.class, () -> FileRename.validateSimpleName("foo/bar.txt"));
        assertThrows(IllegalArgumentException.class, () -> FileRename.validateSimpleName("a<b.txt"));
    }

    @Test
    void testSameNameIsNoOp() throws Exception {
        Path dir = Files.createTempDirectory("lide-rename-same");
        try {
            Path source = dir.resolve("keep.txt");
            Files.writeString(source, "same");
            Path result = FileRename.rename(source, "keep.txt");
            assertTrue(Files.isRegularFile(result), "still there");
            assertEquals("same", Files.readString(result), "unchanged");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testRemapOpenPath() throws Exception {
        Path from = Path.of("proj", "src").toAbsolutePath().normalize();
        Path to = Path.of("proj", "app").toAbsolutePath().normalize();
        Path child = from.resolve("Hello.java");
        Path remapped = FileRename.remapOpenPath(child, from, to);
        assertEquals(to.resolve("Hello.java"), remapped, "child remapped");
        assertEquals(to, FileRename.remapOpenPath(from, from, to), "exact remapped");
        assertTrue(FileRename.remapOpenPath(Path.of("other"), from, to) == null, "unrelated");
    }

    @Test
    void testAbsolutePathText() throws Exception {
        Path path = Path.of("src", "Demo.java");
        assertEquals(path.toAbsolutePath().normalize().toString(), PathClipboard.absolutePath(path), "absolute");
    }

    @Test
    void testRenamesOpenEditor() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        Path root = Files.createTempDirectory("lide-rename-ui");
        try {
            Path source = root.resolve("Hello.java");
            Files.writeString(source, "class Hello {}");
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    frame.editorTabs().openFile(source);
                    Path renamed = frame.renamePath(source, "Hi.java");
                    assertTrue(renamed != null, "renamed path");
                    assertTrue(Files.isRegularFile(root.resolve("Hi.java")), "new file");
                    assertTrue(!Files.exists(source), "old gone");
                    CodeEditor editor = frame.editorTabs().getActiveEditor();
                    assertTrue(editor != null, "editor open");
                    assertEquals(root.resolve("Hi.java").toAbsolutePath().normalize(), editor.getFilePath().toAbsolutePath().normalize(), "editor path");
                    assertEquals("Hi.java", editor.getTitle(), "tab title");
                } finally {
                    frame.dispose();
                }
            });
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testCopyPathToClipboard() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        Path dir = Files.createTempDirectory("lide-copy-path");
        try {
            Path file = dir.resolve("notes.txt");
            Files.writeString(file, "x");
            SwingUtilities.invokeAndWait(() -> PathClipboard.copy(file));
            String clip = clipboardText();
            assertEquals(PathClipboard.absolutePath(file), clip, "clipboard");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testTabCopyPathCopiesAbsolute() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        Path dir = Files.createTempDirectory("lide-tab-copy");
        try {
            Path file = dir.resolve("App.java");
            Files.writeString(file, "class App {}");
            SwingUtilities.invokeAndWait(() -> {
                EditorTabPane pane = new EditorTabPane();
                pane.openFile(file);
                JPopupMenu menu = pane.createTabContextMenu(0);
                JMenuItem copyPath = null;
                for (java.awt.Component component : menu.getComponents()) {
                    if (component instanceof JMenuItem item && "Copy Path".equals(item.getText())) {
                        copyPath = item;
                        break;
                    }
                }
                assertTrue(copyPath != null, "copy path item");
                assertTrue(copyPath.isEnabled(), "copy path enabled");
                copyPath.doClick();
            });
            assertEquals(PathClipboard.absolutePath(file), clipboardText(), "tab clipboard");
        } finally {
            deleteRecursive(dir);
        }
    }

    private static String clipboardText() throws Exception {
        return (String) Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .getData(DataFlavor.stringFlavor);
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
