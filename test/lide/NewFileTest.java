package lide;

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
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testCreatesEmptyFile();
        testCreatesNestedPath();
        testRejectsBlankName();
        testRejectsDotDot();
        testRejectsExistingFile();
        testRejectsAbsolutePath();
        testRejectsTrailingSeparator();
        testRejectsInvalidCharacters();
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: headless environment cannot run new-file UI tests");
        } else {
            SwingUtilities.invokeAndWait(() -> {
                testFileMenuItem();
                testSelectedDirectoryWithoutProject();
            });
            testCreatesAndOpensInEditor();
            testSelectedDirectoryIsProjectRoot();
        }
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testCreatesEmptyFile() throws Exception {
        Path dir = Files.createTempDirectory("lide-new-file");
        try {
            Path created = NewFile.create(dir, "Hello.java");
            assertTrue("exists", Files.isRegularFile(created));
            assertEqual("name", "Hello.java", created.getFileName().toString());
            assertEqual("empty", 0L, Files.size(created));
            assertEqual("parent", dir.toAbsolutePath().normalize(), created.getParent());
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testCreatesNestedPath() throws Exception {
        Path dir = Files.createTempDirectory("lide-new-nested");
        try {
            Path created = NewFile.create(dir, "src/pkg/Hello.java");
            assertTrue("exists", Files.isRegularFile(created));
            assertTrue("parent dirs", Files.isDirectory(dir.resolve("src").resolve("pkg")));
            assertEqual("relative", Path.of("src", "pkg", "Hello.java"),
                    dir.toAbsolutePath().normalize().relativize(created));
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testRejectsBlankName() {
        try {
            NewFile.resolve(Path.of("."), "   ");
            failed++;
            System.err.println("FAIL blank name: expected exception");
        } catch (IllegalArgumentException ex) {
            passed++;
            assertTrue("blank message", ex.getMessage().toLowerCase().contains("file name"));
        }
    }

    private static void testRejectsDotDot() {
        try {
            NewFile.resolve(Path.of("."), "../secret.txt");
            failed++;
            System.err.println("FAIL dot-dot: expected exception");
        } catch (IllegalArgumentException ex) {
            passed++;
        }
    }

    private static void testRejectsExistingFile() throws Exception {
        Path dir = Files.createTempDirectory("lide-new-exists");
        try {
            Files.writeString(dir.resolve("a.txt"), "keep");
            try {
                NewFile.create(dir, "a.txt");
                failed++;
                System.err.println("FAIL existing: expected exception");
            } catch (java.nio.file.FileAlreadyExistsException ex) {
                passed++;
                assertEqual("unchanged", "keep", Files.readString(dir.resolve("a.txt")));
            }
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testRejectsAbsolutePath() {
        Path absolute = Path.of("").toAbsolutePath().resolve("outside.txt");
        try {
            NewFile.resolve(Path.of("."), absolute.toString());
            failed++;
            System.err.println("FAIL absolute: expected exception");
        } catch (IllegalArgumentException ex) {
            passed++;
        }
    }

    private static void testRejectsTrailingSeparator() {
        try {
            NewFile.resolve(Path.of("."), "src/");
            failed++;
            System.err.println("FAIL trailing slash: expected exception");
        } catch (IllegalArgumentException ex) {
            passed++;
        }
    }

    private static void testRejectsInvalidCharacters() {
        try {
            NewFile.resolve(Path.of("."), "a<b.txt");
            failed++;
            System.err.println("FAIL invalid char: expected exception");
        } catch (IllegalArgumentException ex) {
            passed++;
        }
    }

    private static void testFileMenuItem() {
        MainFrame frame = new MainFrame();
        try {
            JMenu file = findMenu(frame.getJMenuBar(), "File");
            assertTrue("File menu exists", file != null);
            JMenuItem item = findMenuItem(file, "New File…");
            assertTrue("New File item exists", item != null);
            assertEqual("accelerator",
                    KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK),
                    item.getAccelerator());
            List<String> labels = itemLabels(file);
            assertEqual("first item", "New File…", labels.get(0));
        } finally {
            frame.dispose();
        }
    }

    private static void testSelectedDirectoryWithoutProject() {
        ProjectTreePanel tree = new ProjectTreePanel();
        assertTrue("no project", tree.getSelectedDirectory() == null);
    }

    private static void testCreatesAndOpensInEditor() throws Exception {
        Path root = Files.createTempDirectory("lide-new-ui");
        try {
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    Path created = frame.createNewFileIn(root, "App.java");
                    assertTrue("created path", created != null);
                    assertTrue("on disk", Files.isRegularFile(root.resolve("App.java")));
                    CodeEditor editor = frame.editorTabs().getActiveEditor();
                    assertTrue("editor open", editor != null);
                    assertEqual("editor file",
                            root.resolve("App.java").toAbsolutePath().normalize(),
                            editor.getFilePath().toAbsolutePath().normalize());
                    assertEqual("empty buffer", "", normalize(editor.getDocumentText()));
                } finally {
                    frame.dispose();
                }
            });
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testSelectedDirectoryIsProjectRoot() throws Exception {
        Path root = Files.createTempDirectory("lide-new-sel");
        try {
            SwingUtilities.invokeAndWait(() -> {
                ProjectTreePanel tree = new ProjectTreePanel();
                tree.openDirectory(root);
                assertEqual("selected dir is root",
                        root.toAbsolutePath().normalize(),
                        tree.getSelectedDirectory().toAbsolutePath().normalize());
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

    private NewFileTest() {
    }
}
