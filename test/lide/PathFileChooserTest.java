package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Tests for pasting a path into file choosers.
 */
public final class PathFileChooserTest {

    @Test
    void testQuotedDirectoryApproves() throws Exception {
        Path dir = Files.createTempDirectory("lide-path-chooser-quote");
        try {
            PathFileChooser.Choice choice = PathFileChooser.choose(
                    "\"" + dir.toAbsolutePath() + "\"",
                    null,
                    JFileChooser.DIRECTORIES_ONLY);
            assertEquals(PathFileChooser.Choice.Action.APPROVE, choice.action, "approve");
            assertEquals(dir.toAbsolutePath().normalize(), choice.path, "path");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testFileUriAndFirstLine() throws Exception {
        Path dir = Files.createTempDirectory("lide-path-chooser-uri");
        try {
            PathFileChooser.Choice fromUri = PathFileChooser.choose(
                    dir.toUri().toString(),
                    null,
                    JFileChooser.DIRECTORIES_ONLY);
            assertEquals(PathFileChooser.Choice.Action.APPROVE, fromUri.action, "uri");
            assertEquals(dir.toAbsolutePath().normalize(), fromUri.path, "uri path");

            PathFileChooser.Choice firstLine = PathFileChooser.choose(
                    dir.toAbsolutePath() + "\r\nignored",
                    null,
                    JFileChooser.DIRECTORIES_ONLY);
            assertEquals(dir.toAbsolutePath().normalize(), firstLine.path, "first line");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testRelativeFileAndFolderNavigation() throws Exception {
        Path root = Files.createTempDirectory("lide-path-chooser-rel");
        try {
            Path child = root.resolve("child");
            Files.createDirectory(child);
            Path file = child.resolve("Note.txt");
            Files.writeString(file, "hi");

            PathFileChooser.Choice relativeDir = PathFileChooser.choose(
                    "child", root, JFileChooser.DIRECTORIES_ONLY);
            assertEquals(PathFileChooser.Choice.Action.APPROVE, relativeDir.action, "relative dir");
            assertEquals(child.toAbsolutePath().normalize(), relativeDir.path, "relative dir path");

            PathFileChooser.Choice relativeFile = PathFileChooser.choose(
                    "child/Note.txt", root, JFileChooser.FILES_ONLY);
            assertEquals(PathFileChooser.Choice.Action.APPROVE, relativeFile.action, "relative file");
            assertEquals(file.toAbsolutePath().normalize(), relativeFile.path, "relative file path");

            PathFileChooser.Choice folderInFileChooser = PathFileChooser.choose(
                    child.toString(), root, JFileChooser.FILES_ONLY);
            assertEquals(PathFileChooser.Choice.Action.NAVIGATE, folderInFileChooser.action, "folder in file chooser");
            assertEquals(child.toAbsolutePath().normalize(), folderInFileChooser.path, "navigate to folder");

            PathFileChooser.Choice fileInDirChooser = PathFileChooser.choose(
                    file.toString(), root, JFileChooser.DIRECTORIES_ONLY);
            assertEquals(PathFileChooser.Choice.Action.NAVIGATE, fileInDirChooser.action, "file in dir chooser");
            assertEquals(child.toAbsolutePath().normalize(), fileInDirChooser.path, "navigate to parent");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testMissingAndBlank() throws Exception {
        Path root = Files.createTempDirectory("lide-path-chooser-miss");
        try {
            PathFileChooser.Choice missing = PathFileChooser.choose(
                    root.resolve("nope").toString(), root, JFileChooser.FILES_ONLY);
            assertEquals(PathFileChooser.Choice.Action.REJECT, missing.action, "missing");
            assertTrue(missing.path == null, "no path");

            PathFileChooser.Choice blankFile = PathFileChooser.choose("   ", root, JFileChooser.FILES_ONLY);
            assertEquals(PathFileChooser.Choice.Action.REJECT, blankFile.action, "blank file");

            PathFileChooser.Choice blankDir = PathFileChooser.choose("  ", root, JFileChooser.DIRECTORIES_ONLY);
            assertEquals(PathFileChooser.Choice.Action.APPROVE, blankDir.action, "blank dir");
            assertEquals(root.toAbsolutePath().normalize(), blankDir.path, "current dir");

            PathFileChooser.Choice invalid = PathFileChooser.choose("bad\u0000name", root, JFileChooser.FILES_ONLY);
            assertEquals(PathFileChooser.Choice.Action.REJECT, invalid.action, "invalid");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testEnterUsesPastedPath() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testEnterUsesPastedPath);
            return;
        }
        Path current = Files.createTempDirectory("lide-path-chooser-cur");
        Path pasted = Files.createTempDirectory("lide-path-chooser-paste");
        try {
            PathFileChooser chooser = new PathFileChooser(JFileChooser.DIRECTORIES_ONLY);
            chooser.setCurrentDirectory(current.toFile());
            chooser.pathField().setText("\"" + pasted.toAbsolutePath() + "\"");
            chooser.pathField().postActionEvent();
            assertTrue(chooser.getSelectedFile() != null, "selected");
            assertEquals(
                    pasted.toAbsolutePath().normalize(),
                    chooser.getSelectedFile().toPath().toAbsolutePath().normalize(),
                    "pasted path");
        } finally {
            deleteRecursive(current);
            deleteRecursive(pasted);
        }
    }

    @Test
    void testOpenButtonUsesEditedPathAndMouseSelectionStillUpdatesField() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testOpenButtonUsesEditedPathAndMouseSelectionStillUpdatesField);
            return;
        }
        Path current = Files.createTempDirectory("lide-path-chooser-open");
        Path pasted = Files.createTempDirectory("lide-path-chooser-open-paste");
        Path file = current.resolve("Note.txt");
        Files.writeString(file, "hi");
        String previousLook = UIManager.getLookAndFeel().getClass().getName();
        JDialog dialog = null;
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            PathFileChooser files = new PathFileChooser(JFileChooser.FILES_ONLY);
            files.setCurrentDirectory(current.toFile());
            files.setSelectedFile(file.toFile());
            assertEquals(
                    file.toAbsolutePath().normalize(),
                    Path.of(files.pathField().getText()).toAbsolutePath().normalize(),
                    "click updates path");

            PathFileChooser chooser = new PathFileChooser(JFileChooser.DIRECTORIES_ONLY);
            chooser.setCurrentDirectory(current.toFile());
            dialog = chooser.createDialog(null);
            chooser.pathField().setText(pasted.toAbsolutePath().toString());
            JButton open = findOpenButton(dialog);
            assertTrue(open != null, "open button");
            open.doClick();
            assertEquals(
                    pasted.toAbsolutePath().normalize(),
                    chooser.getSelectedFile().toPath().toAbsolutePath().normalize(),
                    "edited open");
        } finally {
            if (dialog != null) {
                dialog.dispose();
            }
            deleteRecursive(current);
            deleteRecursive(pasted);
            UIManager.setLookAndFeel(previousLook);
        }
    }

    @Test
    void testPastedFolderMovesFileChooser() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testPastedFolderMovesFileChooser);
            return;
        }
        Path root = Files.createTempDirectory("lide-path-chooser-nav");
        try {
            Path child = root.resolve("child");
            Files.createDirectory(child);
            PathFileChooser chooser = new PathFileChooser(JFileChooser.FILES_ONLY);
            chooser.setCurrentDirectory(root.toFile());
            chooser.pathField().setText(child.toString());
            chooser.applyTypedPath();
            assertEquals(
                    child.toAbsolutePath().normalize(),
                    chooser.getCurrentDirectory().toPath().toAbsolutePath().normalize(),
                    "moved");
            assertTrue(chooser.getSelectedFile() == null, "still open");
        } finally {
            deleteRecursive(root);
        }
    }

    private static JButton findOpenButton(Component component) {
        if (component instanceof JButton button) {
            String text = button.getText();
            if (text != null && !text.isBlank() && !"Cancel".equalsIgnoreCase(text)) {
                return button;
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JButton found = findOpenButton(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
