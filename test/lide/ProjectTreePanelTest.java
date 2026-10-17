package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

/**
 * Tests for the project tree toolbar Refresh button.
 */
public final class ProjectTreePanelTest {

    @BeforeEach
    void requireDisplay() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "headless environment cannot run ProjectTreePanelTest");
    }

    @Test
    void testRefreshDisabledWithoutProject() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testRefreshDisabledWithoutProject);
            return;
        }
        ProjectTreePanel tree = new ProjectTreePanel();
        JButton refresh = tree.getRefreshButton();
        assertTrue(refresh != null, "refresh button present");
        assertTrue(refresh.getText() == null || refresh.getText().isEmpty(), "no text label");
        assertTrue(refresh.getIcon() instanceof ProjectTreePanel.RefreshIcon, "uses refresh icon");
        assertEquals("Refresh", refresh.getToolTipText(), "tooltip");
        assertEquals(ProjectTreePanel.RefreshIcon.SIZE, refresh.getIcon().getIconWidth(), "icon width");
        assertTrue(!refresh.isEnabled(), "disabled without project");
        JButton select = tree.getSelectButton();
        assertEquals("Select", select.getText(), "select label");
        assertEquals("Select the open file in the project tree", select.getToolTipText(), "select tooltip");
        assertTrue(!select.isEnabled(), "select disabled without project");
        tree.refresh();
        assertTrue(tree.getProjectRoot() == null, "still no project");
        select.doClick();
        assertTrue(tree.getSelectedPath() == null, "select without a project does nothing");
    }

    @Test
    void testRefreshIconPaints() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testRefreshIconPaints);
            return;
        }
        ProjectTreePanel.RefreshIcon icon = new ProjectTreePanel.RefreshIcon();
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                icon.getIconWidth(), icon.getIconHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        try {
            icon.paintIcon(null, g, 0, 0);
        } finally {
            g.dispose();
        }
        boolean painted = false;
        for (int y = 0; y < image.getHeight() && !painted; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    painted = true;
                    break;
                }
            }
        }
        assertTrue(painted, "icon draws pixels");
    }

    @Test
    void testRefreshEnabledWhenProjectOpen() throws Exception {
        Path root = Files.createTempDirectory("lide-tree-refresh-enable");
        try {
            SwingUtilities.invokeAndWait(() -> {
                ProjectTreePanel tree = new ProjectTreePanel();
                tree.openDirectory(root);
                assertTrue(tree.getRefreshButton().isEnabled(), "enabled with project");
                assertTrue(tree.getSelectButton().isEnabled(), "select enabled with project");
            });
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testRefreshPicksUpNewFile() throws Exception {
        Path root = Files.createTempDirectory("lide-tree-refresh-files");
        try {
            Files.writeString(root.resolve("first.txt"), "one");
            ProjectTreePanel tree = new ProjectTreePanel();
            SwingUtilities.invokeAndWait(() -> tree.openDirectory(root));
            assertTrue(waitForChild(tree, "first.txt", 5000), "first.txt after open");
            assertTrue(!tree.rootChildNames().contains("second.txt"), "second.txt not yet");

            Files.writeString(root.resolve("second.txt"), "two");
            SwingUtilities.invokeAndWait(() -> tree.getRefreshButton().doClick());
            assertTrue(waitForChild(tree, "second.txt", 5000), "second.txt after refresh");
            assertTrue(tree.rootChildNames().contains("first.txt"), "first.txt still there");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testSelectsOpenFileInNestedFolder() throws Exception {
        Path root = Files.createTempDirectory("lide-tree-select-nested");
        try {
            Path file = root.resolve("src").resolve("lide").resolve("Hello.java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "class Hello {}");
            Path sibling = root.resolve("README.md");
            Files.writeString(sibling, "hi");
            ProjectTreePanel tree = new ProjectTreePanel();
            SwingUtilities.invokeAndWait(() -> {
                tree.openDirectory(root);
                tree.setActiveFileSupplier(() -> file);
                tree.getSelectButton().doClick();
            });
            assertTrue(waitForSelection(tree, file, 5000), "nested file selected");
            int[] row = {-1};
            SwingUtilities.invokeAndWait(() -> row[0] = tree.getSelectedRow());
            assertTrue(row[0] >= 0, "selected row is expanded into view");

            SwingUtilities.invokeAndWait(() -> {
                tree.setActiveFileSupplier(() -> root.getParent().resolve("outside.txt"));
                tree.getSelectButton().doClick();
            });
            assertEquals(file.toAbsolutePath().normalize(), selectedPath(tree), "outside file leaves selection");

            SwingUtilities.invokeAndWait(() -> {
                tree.setActiveFileSupplier(() -> null);
                tree.getSelectButton().doClick();
            });
            assertEquals(file.toAbsolutePath().normalize(), selectedPath(tree), "no editor file leaves selection");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testSelectIgnoresSkippedFolder() throws Exception {
        Path root = Files.createTempDirectory("lide-tree-select-skip");
        try {
            Path visible = root.resolve("README.md");
            Files.writeString(visible, "hi");
            Path hidden = root.resolve("out").resolve("skip.txt");
            Files.createDirectories(hidden.getParent());
            Files.writeString(hidden, "nope");
            ProjectTreePanel tree = new ProjectTreePanel();
            SwingUtilities.invokeAndWait(() -> {
                tree.openDirectory(root);
                tree.setActiveFileSupplier(() -> visible);
                tree.getSelectButton().doClick();
            });
            assertTrue(waitForSelection(tree, visible, 5000), "visible file selected");
            SwingUtilities.invokeAndWait(() -> {
                tree.setActiveFileSupplier(() -> hidden);
                tree.getSelectButton().doClick();
            });
            assertEquals(visible.toAbsolutePath().normalize(), selectedPath(tree), "skipped folder not selected");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testSelectButtonUsesEditorFile() throws Exception {
        Path root = Files.createTempDirectory("lide-tree-select-editor");
        try {
            Path file = root.resolve("src").resolve("App.java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "class App {}");
            MainFrame[] frame = new MainFrame[1];
            SwingUtilities.invokeAndWait(() -> {
                frame[0] = new MainFrame();
                frame[0].applyProjectDirectory(root);
                frame[0].editorTabs().openFile(file);
                frame[0].projectTree().getSelectButton().doClick();
            });
            try {
                assertTrue(waitForSelection(frame[0].projectTree(), file, 5000), "editor file selected");
            } finally {
                SwingUtilities.invokeAndWait(() -> frame[0].dispose());
            }
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testSkipNameKeepsDotfiles() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testSkipNameKeepsDotfiles);
            return;
        }
        assertTrue(!ProjectTreePanel.skipName(".sdkmanrc"), "sdkmanrc shown");
        assertTrue(!ProjectTreePanel.skipName(".gitignore"), "gitignore shown");
        assertTrue(!ProjectTreePanel.skipName(".sdkman"), "sdkman dir shown");
        assertTrue(!ProjectTreePanel.skipName(".editorconfig"), "editorconfig shown");
        assertTrue(ProjectTreePanel.skipName(".git"), "skips .git");
        assertTrue(ProjectTreePanel.skipName(".idea"), "skips .idea");
        assertTrue(ProjectTreePanel.skipName("out"), "skips out");
        assertTrue(ProjectTreePanel.skipName("build"), "skips build");
        assertTrue(!ProjectTreePanel.skipName(null), "null not skipped as a name match");
        assertTrue(!ProjectTreePanel.skipName("Main.java"), "plain file shown");
    }

    @Test
    void testShowsDotfilesAndSkipsGit() throws Exception {
        Path root = Files.createTempDirectory("lide-tree-dotfiles");
        try {
            Files.writeString(root.resolve(".sdkmanrc"), "java=21.0.2-tem\n");
            Files.writeString(root.resolve(".gitignore"), "out/\n");
            Files.writeString(root.resolve("README.md"), "hi\n");
            Path git = root.resolve(".git");
            Files.createDirectories(git);
            Files.writeString(git.resolve("HEAD"), "ref: refs/heads/main\n");
            Path out = root.resolve("out");
            Files.createDirectories(out);
            Files.writeString(out.resolve("skip.txt"), "nope\n");

            ProjectTreePanel tree = new ProjectTreePanel();
            SwingUtilities.invokeAndWait(() -> tree.openDirectory(root));
            assertTrue(waitForChild(tree, ".sdkmanrc", 5000), ".sdkmanrc visible");
            assertTrue(tree.rootChildNames().contains(".gitignore"), ".gitignore visible");
            assertTrue(tree.rootChildNames().contains("README.md"), "README visible");
            assertTrue(!tree.rootChildNames().contains(".git"), ".git hidden");
            assertTrue(!tree.rootChildNames().contains("out"), "out hidden");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testSelectedDirectoryIsReadable() throws Exception {
        Path root = Files.createTempDirectory("lide-tree-select");
        try {
            Files.createDirectories(root.resolve("src"));
            Files.writeString(root.resolve("src").resolve("A.java"), "class A {}");
            SwingUtilities.invokeAndWait(() -> {
                try {
                    javax.swing.UIManager.setLookAndFeel(
                            javax.swing.UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ex) {
                    AppLog.exception("Could not set system look and feel for tree selection test", ex);
                }
                IdeTheme.apply();
                ProjectTreePanel tree = new ProjectTreePanel();
                tree.openDirectory(root);
                assertSelectedRowReadable(tree.renderCell(tree.projectRootValue(), true, false), "directory");
                assertSelectedRowReadable(tree.renderCell("A.java", true, true), "file");
                Component plain = tree.renderCell(tree.projectRootValue(), false, false);
                assertEquals(IdeTheme.FG, plain.getForeground(), "unselected text");
                int edge = paintEdge(plain);
                assertEquals(IdeTheme.BG_TREE.getRGB(), edge, "unselected row stays light");
            });
        } finally {
            deleteRecursive(root);
        }
    }

    private static void assertSelectedRowReadable(Component row, String kind) {
        assertEquals(Color.WHITE, row.getForeground(), kind + " selected text");
        assertEquals(IdeTheme.SELECTION, row.getBackground(), kind + " selected fill");
        int edge = paintEdge(row);
        assertEquals(IdeTheme.SELECTION.getRGB(), edge, kind + " selection is dark");
        assertTrue(hasNearWhitePixel(paint(row)), kind + " label is visible");
    }

    private static int paintEdge(Component row) {
        BufferedImage image = paint(row);
        return image.getRGB(image.getWidth() - 2, image.getHeight() / 2);
    }

    private static boolean hasNearWhitePixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r > 240 && g > 240 && b > 240) {
                    return true;
                }
            }
        }
        return false;
    }

    private static BufferedImage paint(Component row) {
        Dimension pref = row.getPreferredSize();
        int width = Math.max(pref.width + 40, 180);
        int height = Math.max(pref.height, 22);
        row.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.MAGENTA);
            g.fillRect(0, 0, width, height);
            row.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static Path selectedPath(ProjectTreePanel tree) throws Exception {
        Path[] path = new Path[1];
        SwingUtilities.invokeAndWait(() -> path[0] = tree.getSelectedPath());
        return path[0];
    }

    private static boolean waitForSelection(ProjectTreePanel tree, Path expected, long timeoutMs)
            throws Exception {
        Path target = expected.toAbsolutePath().normalize();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (target.equals(selectedPath(tree))) {
                return true;
            }
            Thread.sleep(50);
        }
        System.err.println("timed out waiting to select " + target + " but had " + selectedPath(tree));
        return false;
    }

    private static boolean waitForChild(ProjectTreePanel tree, String name, long timeoutMs)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            boolean[] found = {false};
            SwingUtilities.invokeAndWait(() -> found[0] = tree.rootChildNames().contains(name));
            if (found[0]) {
                return true;
            }
            Thread.sleep(50);
        }
        java.util.concurrent.atomic.AtomicReference<List<String>> names =
                new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> names.set(tree.rootChildNames()));
        System.err.println("timed out waiting for " + name + " in " + names.get());
        return false;
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
