package lide;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 * Tests for the Go to Class popup panel.
 */
public final class GoToClassDialogTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: headless environment cannot run Go to Class UI tests");
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            testFiltersAsYouType();
            testEnterOpensSelected();
            testEmptyQueryShowsNothing();
        });
        testOpenClassHitJumpsToDeclaration();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testFiltersAsYouType() {
        Path a = Path.of("src", "ClassNavigator.java");
        Path b = Path.of("src", "CodeEditor.java");
        List<ClassSearch.Hit> all = List.of(
                new ClassSearch.Hit("ClassNavigator", a, "src/ClassNavigator.java"),
                new ClassSearch.Hit("CodeEditor", b, "src/CodeEditor.java"));
        GoToClassDialog.Panel panel = GoToClassDialog.create(all, hit -> {
        }, () -> {
        });
        panel.setQuery("Class");
        assertEqual("one match", 1, panel.model.size());
        assertEqual("navigator", "ClassNavigator", panel.model.get(0).className());
        assertEqual("selected", 0, panel.results.getSelectedIndex());
    }

    private static void testEnterOpensSelected() {
        Path a = Path.of("src", "Foo.java");
        Path b = Path.of("src", "Bar.java");
        List<ClassSearch.Hit> all = List.of(
                new ClassSearch.Hit("Foo", a, "src/Foo.java"),
                new ClassSearch.Hit("Bar", b, "src/Bar.java"));
        AtomicReference<ClassSearch.Hit> opened = new AtomicReference<>();
        GoToClassDialog.Panel panel = GoToClassDialog.create(all, opened::set, () -> {
        });
        panel.setQuery("Bar");
        panel.chooseSelected();
        assertTrue("opened", opened.get() != null);
        assertEqual("bar", "Bar", opened.get().className());
    }

    private static void testEmptyQueryShowsNothing() {
        List<ClassSearch.Hit> all = List.of(
                new ClassSearch.Hit("Foo", Path.of("Foo.java"), "Foo.java"));
        GoToClassDialog.Panel panel = GoToClassDialog.create(all, hit -> {
        }, () -> {
        });
        panel.setQuery("");
        assertEqual("empty list", 0, panel.model.size());
    }

    private static void testOpenClassHitJumpsToDeclaration() throws Exception {
        Path root = Files.createTempDirectory("lide-goto-open");
        try {
            Path file = root.resolve("Foo.java");
            String source = "package demo;\n\npublic class Foo {\n}\n";
            Files.writeString(file, source);
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.applyProjectDirectory(root);
                    frame.openClassHit(new ClassSearch.Hit("Foo", file, "Foo.java"));
                    assertEqual("opened file", file.toAbsolutePath().normalize(),
                            frame.editorTabs().getActiveFilePath());
                    int expected = source.replace("\r\n", "\n").indexOf("Foo", source.indexOf("class"));
                    assertEqual("caret on class name", expected,
                            frame.editorTabs().getActiveEditor().getCaretPosition());
                } finally {
                    frame.dispose();
                }
            });
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

    private GoToClassDialogTest() {
    }
}
