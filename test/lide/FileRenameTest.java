package lide;

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
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testRenamesFile();
        testRenamesDirectory();
        testRejectsExistingTarget();
        testRejectsBlankAndSeparators();
        testSameNameIsNoOp();
        testRemapOpenPath();
        testAbsolutePathText();
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: headless environment cannot run rename UI tests");
        } else {
            testRenamesOpenEditor();
            testCopyPathToClipboard();
            testTabCopyPathCopiesAbsolute();
        }
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testRenamesFile() throws Exception {
        Path dir = Files.createTempDirectory("lide-rename-file");
        try {
            Path source = dir.resolve("old.txt");
            Files.writeString(source, "hello");
            Path renamed = FileRename.rename(source, "new.txt");
            assertTrue("old gone", !Files.exists(source));
            assertTrue("new exists", Files.isRegularFile(renamed));
            assertEqual("name", "new.txt", renamed.getFileName().toString());
            assertEqual("content", "hello", Files.readString(renamed));
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testRenamesDirectory() throws Exception {
        Path dir = Files.createTempDirectory("lide-rename-dir");
        try {
            Path source = Files.createDirectory(dir.resolve("pkg"));
            Files.writeString(source.resolve("A.java"), "class A {}");
            Path renamed = FileRename.rename(source, "other");
            assertTrue("old gone", !Files.exists(source));
            assertTrue("dir moved", Files.isDirectory(renamed));
            assertTrue("child moved", Files.isRegularFile(renamed.resolve("A.java")));
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testRejectsExistingTarget() throws Exception {
        Path dir = Files.createTempDirectory("lide-rename-exists");
        try {
            Path source = dir.resolve("a.txt");
            Files.writeString(source, "a");
            Files.writeString(dir.resolve("b.txt"), "b");
            try {
                FileRename.rename(source, "b.txt");
                failed++;
                System.err.println("FAIL existing target: expected exception");
            } catch (java.nio.file.FileAlreadyExistsException ex) {
                passed++;
                assertEqual("source kept", "a", Files.readString(source));
            }
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testRejectsBlankAndSeparators() {
        try {
            FileRename.validateSimpleName("  ");
            failed++;
            System.err.println("FAIL blank: expected exception");
        } catch (IllegalArgumentException ex) {
            passed++;
        }
        try {
            FileRename.validateSimpleName("foo/bar.txt");
            failed++;
            System.err.println("FAIL slash: expected exception");
        } catch (IllegalArgumentException ex) {
            passed++;
        }
        try {
            FileRename.validateSimpleName("a<b.txt");
            failed++;
            System.err.println("FAIL illegal char: expected exception");
        } catch (IllegalArgumentException ex) {
            passed++;
        }
    }

    private static void testSameNameIsNoOp() throws Exception {
        Path dir = Files.createTempDirectory("lide-rename-same");
        try {
            Path source = dir.resolve("keep.txt");
            Files.writeString(source, "same");
            Path result = FileRename.rename(source, "keep.txt");
            assertTrue("still there", Files.isRegularFile(result));
            assertEqual("unchanged", "same", Files.readString(result));
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testRemapOpenPath() {
        Path from = Path.of("proj", "src").toAbsolutePath().normalize();
        Path to = Path.of("proj", "app").toAbsolutePath().normalize();
        Path child = from.resolve("Hello.java");
        Path remapped = FileRename.remapOpenPath(child, from, to);
        assertEqual("child remapped", to.resolve("Hello.java"), remapped);
        assertEqual("exact remapped", to, FileRename.remapOpenPath(from, from, to));
        assertTrue("unrelated", FileRename.remapOpenPath(Path.of("other"), from, to) == null);
    }

    private static void testAbsolutePathText() {
        Path path = Path.of("src", "Demo.java");
        assertEqual("absolute", path.toAbsolutePath().normalize().toString(),
                PathClipboard.absolutePath(path));
    }

    private static void testRenamesOpenEditor() throws Exception {
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
                    assertTrue("renamed path", renamed != null);
                    assertTrue("new file", Files.isRegularFile(root.resolve("Hi.java")));
                    assertTrue("old gone", !Files.exists(source));
                    CodeEditor editor = frame.editorTabs().getActiveEditor();
                    assertTrue("editor open", editor != null);
                    assertEqual("editor path",
                            root.resolve("Hi.java").toAbsolutePath().normalize(),
                            editor.getFilePath().toAbsolutePath().normalize());
                    assertEqual("tab title", "Hi.java", editor.getTitle());
                } finally {
                    frame.dispose();
                }
            });
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testCopyPathToClipboard() throws Exception {
        Path dir = Files.createTempDirectory("lide-copy-path");
        try {
            Path file = dir.resolve("notes.txt");
            Files.writeString(file, "x");
            SwingUtilities.invokeAndWait(() -> PathClipboard.copy(file));
            String clip = clipboardText();
            assertEqual("clipboard", PathClipboard.absolutePath(file), clip);
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testTabCopyPathCopiesAbsolute() throws Exception {
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
                assertTrue("copy path item", copyPath != null);
                assertTrue("copy path enabled", copyPath.isEnabled());
                copyPath.doClick();
            });
            assertEqual("tab clipboard", PathClipboard.absolutePath(file), clipboardText());
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

    private FileRenameTest() {
    }
}
