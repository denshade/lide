package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Assumptions;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

/**
 * Tests for creating a Java class in the package of the open file.
 */
public final class NewJavaClassTest {

    @Test
    void testSourceUsesOpenPackage() {
        String source = NewJavaClass.sourceText("lide.util", "Widget");
        assertEquals("package lide.util;\n\npublic class Widget {\n}\n", source, "source");
    }

    @Test
    void testSourceOmitsDefaultPackage() {
        String source = NewJavaClass.sourceText("", "Widget");
        assertEquals("public class Widget {\n}\n", source, "default package");
        assertTrue(!source.contains("package"), "no package line");
    }

    @Test
    void testStripsJavaExtension() {
        assertEquals("Widget", NewJavaClass.className("  Widget.java  "), "stripped");
        assertEquals("Widget", NewJavaClass.className("Widget.JAVA"), "case");
    }

    @Test
    void testRejectsInvalidClassNames() {
        assertThrows(IllegalArgumentException.class, () -> NewJavaClass.className("   "));
        assertThrows(IllegalArgumentException.class, () -> NewJavaClass.className(".java"));
        assertThrows(IllegalArgumentException.class, () -> NewJavaClass.className("class"));
        assertThrows(IllegalArgumentException.class, () -> NewJavaClass.className("com.example.Widget"));
        assertThrows(IllegalArgumentException.class, () -> NewJavaClass.className("123Bad"));
    }

    @Test
    void testNamespaceFromOpenFile() throws Exception {
        Path dir = Files.createTempDirectory("lide-java-ns");
        try {
            Path file = dir.resolve("Existing.java");
            Files.writeString(file, "/* header */\npackage com.example;\r\n\npublic class Existing {\n}\n");
            NewJavaClass.Namespace namespace = NewJavaClass.namespaceOf(file, Files.readString(file));
            assertEquals(dir.toAbsolutePath().normalize(), namespace.directory(), "directory");
            assertEquals("com.example", namespace.packageName(), "package");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testNamespaceWithoutPackage() throws Exception {
        Path dir = Files.createTempDirectory("lide-java-default");
        try {
            Path file = dir.resolve("App.java");
            String source = "public class App {\n}\n";
            Files.writeString(file, source);
            NewJavaClass.Namespace namespace = NewJavaClass.namespaceOf(file, source);
            assertEquals("", namespace.packageName(), "default package");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testRejectsInvalidPackage() {
        assertThrows(IllegalArgumentException.class,
                () -> NewJavaClass.namespaceOf(Path.of("."), "package lide.;\n"));
    }

    @Test
    void testCreatesClassBesideOpenFile() throws Exception {
        Path dir = Files.createTempDirectory("lide-java-create");
        try {
            Path existing = dir.resolve("Existing.java");
            Files.writeString(existing, "package lide;\n\npublic class Existing {\n}\n");
            NewJavaClass.Namespace namespace = NewJavaClass.namespaceOf(existing, Files.readString(existing));
            Path created = NewJavaClass.create(namespace.directory(), namespace.packageName(), "Widget");
            assertEquals(dir.resolve("Widget.java").toAbsolutePath().normalize(), created, "path");
            assertEquals(
                    "package lide;\n\npublic class Widget {\n}\n",
                    Files.readString(created).replace("\r\n", "\n"),
                    "contents");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testDoesNotOverwriteExistingClass() throws Exception {
        Path dir = Files.createTempDirectory("lide-java-exists");
        try {
            Files.writeString(dir.resolve("Widget.java"), "keep");
            java.nio.file.FileAlreadyExistsException ex = assertThrows(
                    java.nio.file.FileAlreadyExistsException.class,
                    () -> NewJavaClass.create(dir, "lide", "Widget"));
            assertEquals("keep", Files.readString(dir.resolve("Widget.java")), "unchanged");
            assertTrue(ex.getMessage() != null && !ex.getMessage().isBlank(), "message");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testFileMenuItem() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();
            try {
                JMenu file = findMenu(frame.getJMenuBar(), "File");
                assertTrue(file != null, "File menu exists");
                JMenuItem item = findMenuItem(file, "New Java Class…");
                assertTrue(item != null, "New Java Class item exists");
                List<String> labels = itemLabels(file);
                assertEquals("New File…", labels.get(0), "new file stays first");
                assertEquals("New Java Class…", labels.get(1), "under New File");
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void testPackageFromSelectedDirectory() throws Exception {
        Path root = Files.createTempDirectory("lide-java-dir");
        try {
            Path pkg = root.resolve("src").resolve("lide");
            Files.createDirectories(pkg);
            Files.writeString(pkg.resolve("Existing.java"), "package lide;\n\npublic class Existing {\n}\n");
            NewJavaClass.Namespace namespace = NewJavaClass.namespaceFor(root, pkg);
            assertEquals(pkg.toAbsolutePath().normalize(), namespace.directory(), "selected folder");
            assertEquals("lide", namespace.packageName(), "sibling package");

            Path empty = root.resolve("src").resolve("other");
            Files.createDirectories(empty);
            NewJavaClass.Namespace inferred = NewJavaClass.namespaceFor(root, empty);
            assertEquals("other", inferred.packageName(), "path under src");

            Path maven = root.resolve("src").resolve("main").resolve("java").resolve("com").resolve("example");
            Files.createDirectories(maven);
            assertEquals("com.example", NewJavaClass.namespaceFor(root, maven).packageName(), "maven source root");

            Path tests = root.resolve("test").resolve("lide");
            Files.createDirectories(tests);
            assertEquals("lide", NewJavaClass.namespaceFor(root, tests).packageName(), "test source root");

            assertEquals("", NewJavaClass.namespaceFor(root, root.resolve("src")).packageName(), "src itself");
            assertEquals("", NewJavaClass.namespaceFor(root, root).packageName(), "project root");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testSiblingDefaultPackageWinsOverPath() throws Exception {
        Path root = Files.createTempDirectory("lide-java-default-dir");
        try {
            Path pkg = root.resolve("src").resolve("lide");
            Files.createDirectories(pkg);
            Files.writeString(pkg.resolve("App.java"), "public class App {\n}\n");
            assertEquals("", NewJavaClass.namespaceFor(root, pkg).packageName(), "default package in folder");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testCreatesAndOpensInEditor() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        Path root = Files.createTempDirectory("lide-java-ui");
        try {
            Path pkg = root.resolve("src").resolve("lide");
            Files.createDirectories(pkg);
            Path[] createdHolder = new Path[1];
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    assertTrue(frame.editorTabs().getActiveEditor() == null, "no file open");
                    Path created = frame.createNewJavaClass(pkg, "Widget.java");
                    createdHolder[0] = created;
                    assertTrue(created != null, "created path");
                    assertEquals(pkg.resolve("Widget.java").toAbsolutePath().normalize(), created, "in selected folder");
                    CodeEditor editor = frame.editorTabs().getActiveEditor();
                    assertTrue(editor != null, "editor open");
                    assertEquals(created, editor.getFilePath().toAbsolutePath().normalize(), "editor file");
                    assertEquals(
                            "package lide;\n\npublic class Widget {\n}\n",
                            editor.getDocumentText().replace("\r\n", "\n"),
                            "editor text");
                } finally {
                    frame.dispose();
                }
            });
            assertEquals(
                    "package lide;\n\npublic class Widget {\n}\n",
                    Files.readString(createdHolder[0]).replace("\r\n", "\n"),
                    "contents");
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
