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
        tree.refresh();
        assertTrue(tree.getProjectRoot() == null, "still no project");
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
