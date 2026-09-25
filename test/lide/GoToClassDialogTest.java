package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void requireDisplay() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "headless environment cannot run GoToClassDialogTest");
    }

    @Test
    void testFiltersAsYouType() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testFiltersAsYouType);
            return;
        }
        Path a = Path.of("src", "ClassNavigator.java");
        Path b = Path.of("src", "CodeEditor.java");
        List<ClassSearch.Hit> all = List.of(
                new ClassSearch.Hit("ClassNavigator", a, "src/ClassNavigator.java"),
                new ClassSearch.Hit("CodeEditor", b, "src/CodeEditor.java"));
        GoToClassDialog.Panel panel = GoToClassDialog.create(all, hit -> {
        }, () -> {
        });
        panel.setQuery("Class");
        assertEquals(1, panel.model.size(), "one match");
        assertEquals("ClassNavigator", panel.model.get(0).className(), "navigator");
        assertEquals(0, panel.results.getSelectedIndex(), "selected");
    }

    @Test
    void testEnterOpensSelected() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testEnterOpensSelected);
            return;
        }
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
        assertTrue(opened.get() != null, "opened");
        assertEquals("Bar", opened.get().className(), "bar");
    }

    @Test
    void testEmptyQueryShowsNothing() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testEmptyQueryShowsNothing);
            return;
        }
        List<ClassSearch.Hit> all = List.of(
                new ClassSearch.Hit("Foo", Path.of("Foo.java"), "Foo.java"));
        GoToClassDialog.Panel panel = GoToClassDialog.create(all, hit -> {
        }, () -> {
        });
        panel.setQuery("");
        assertEquals(0, panel.model.size(), "empty list");
    }

    @Test
    void testOpenClassHitJumpsToDeclaration() throws Exception {
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
                    assertEquals(file.toAbsolutePath().normalize(), frame.editorTabs().getActiveFilePath(), "opened file");
                    int expected = source.replace("\r\n", "\n").indexOf("Foo", source.indexOf("class"));
                    assertEquals(expected, frame.editorTabs().getActiveEditor().getCaretPosition(), "caret on class name");
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

}
