package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

/**
 * F6 inside a test editor runs that class; elsewhere it runs the full suite.
 */
public final class FocusedTestHotkeyTest {

    @Test
    void testF6TargetsTheOpenTestFile() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        Path dir = Files.createTempDirectory("lide-f6-test");
        try {
            Path testFile = dir.resolve("WidgetTest.java");
            Path mainFile = dir.resolve("Widget.java");
            Files.writeString(testFile, "class WidgetTest {}");
            Files.writeString(mainFile, "class Widget {}");
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                try {
                    frame.editorTabs().openFile(testFile);
                    CodeEditor editor = frame.editorTabs().getActiveEditor();
                    assertTrue(editor != null, "editor");
                    JTextPane text = editor.getTextPane();
                    assertEquals(
                            testFile.toAbsolutePath().normalize(),
                            frame.focusedTestFile(text),
                            "F6 in the test editor");
                    assertEquals(
                            testFile.toAbsolutePath().normalize(),
                            frame.focusedTestFile(editor),
                            "editor panel counts");
                    assertTrue(frame.focusedTestFile(new JPanel()) == null, "outside the editor runs the suite");
                    assertTrue(frame.focusedTestFile(null) == null, "no focus runs the suite");

                    frame.editorTabs().openFile(mainFile);
                    CodeEditor mainEditor = frame.editorTabs().getActiveEditor();
                    assertTrue(mainEditor != null, "main editor");
                    assertTrue(frame.focusedTestFile(mainEditor.getTextPane()) == null, "non-test file runs the suite");
                    assertTrue(frame.focusedTestFile(text) == null, "background test tab is not the target");
                } finally {
                    frame.dispose();
                }
            });
        } finally {
            deleteRecursive(dir);
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
