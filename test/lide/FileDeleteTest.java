package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * Tests for deleting a file from the project tree.
 */
public final class FileDeleteTest {

    @Test
    void testDeletesFile() throws Exception {
        Path dir = Files.createTempDirectory("lide-delete-file");
        try {
            Path source = dir.resolve("old.txt");
            Path sibling = dir.resolve("keep.txt");
            Files.writeString(source, "hello");
            Files.writeString(sibling, "stay");
            FileDelete.delete(source);
            assertTrue(!Files.exists(source), "deleted");
            assertEquals("stay", Files.readString(sibling), "sibling kept");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testRefusesDirectory() throws Exception {
        Path dir = Files.createTempDirectory("lide-delete-dir");
        try {
            Path child = Files.createDirectory(dir.resolve("pkg"));
            Files.writeString(child.resolve("A.java"), "class A {}");
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class, () -> FileDelete.delete(child));
            assertEquals("Cannot delete a folder", ex.getMessage(), "message");
            assertTrue(Files.isDirectory(child), "folder kept");
            assertTrue(Files.isRegularFile(child.resolve("A.java")), "child kept");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testRejectsMissingAndNull() throws Exception {
        Path dir = Files.createTempDirectory("lide-delete-missing");
        try {
            assertThrows(NoSuchFileException.class, () -> FileDelete.delete(dir.resolve("missing.txt")));
            assertThrows(IllegalArgumentException.class, () -> FileDelete.delete(null));
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testDeletesOpenFileAndDiscardsEdits() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        Path root = Files.createTempDirectory("lide-delete-ui");
        try {
            Path source = root.resolve("Hello.java");
            Path keep = root.resolve("Keep.java");
            Files.writeString(source, "class Hello {}");
            Files.writeString(keep, "class Keep {}");
            Path[] deleted = new Path[1];
            int[] tabs = new int[1];
            Path[] active = new Path[1];
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    frame.editorTabs().openFile(source);
                    frame.editorTabs().openFile(keep);
                    CodeEditor editor = frame.editorTabs().getActiveEditor();
                    assertTrue(editor != null, "editor open");
                    editor.getTextPane().setText("class Hello { int x; }");
                    assertTrue(editor.isDirty(), "dirty before delete");
                    deleted[0] = frame.deletePath(source);
                    tabs[0] = frame.editorTabs().getTabCount();
                    active[0] = frame.editorTabs().getActiveFilePath();
                } finally {
                    frame.dispose();
                }
            });
            assertTrue(deleted[0] != null, "deleted path");
            assertTrue(!Files.exists(source), "file gone");
            assertEquals("class Keep {}", Files.readString(keep), "sibling kept");
            assertEquals(1, tabs[0], "only sibling tab");
            assertEquals(keep.toAbsolutePath().normalize(), active[0], "sibling still open");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testDeletesOpenBinaryFile() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        Path root = Files.createTempDirectory("lide-delete-bin");
        try {
            Path image = root.resolve("pic.png");
            Files.write(image, new byte[] {1, 2, 3, 4});
            Path[] deleted = new Path[1];
            int[] tabs = new int[1];
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    frame.editorTabs().openFile(image);
                    assertTrue(frame.editorTabs().getActiveEditor() == null, "opened as binary");
                    assertEquals(1, frame.editorTabs().getTabCount(), "binary tab open");
                    deleted[0] = frame.deletePath(image);
                    tabs[0] = frame.editorTabs().getTabCount();
                } finally {
                    frame.dispose();
                }
            });
            assertTrue(deleted[0] != null, "deleted path");
            assertTrue(!Files.exists(image), "file gone");
            assertEquals(0, tabs[0], "tab closed");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testDeletePathLeavesFolder() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        Path root = Files.createTempDirectory("lide-delete-folder");
        try {
            Path folder = Files.createDirectory(root.resolve("src"));
            Files.writeString(folder.resolve("A.java"), "class A {}");
            boolean[] refused = new boolean[2];
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    refused[0] = frame.deletePath(folder) == null;
                    refused[1] = frame.deletePath(root) == null;
                } finally {
                    frame.dispose();
                }
            });
            assertTrue(refused[0], "folder not deleted");
            assertTrue(refused[1], "project root not deleted");
            assertTrue(Files.isDirectory(folder), "folder kept");
            assertEquals("class A {}", Files.readString(folder.resolve("A.java")), "child kept");
        } finally {
            deleteRecursive(root);
        }
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
