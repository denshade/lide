package lide;

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
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: headless environment cannot run project tree tests");
            return;
        }
        SwingUtilities.invokeAndWait(ProjectTreePanelTest::testRefreshDisabledWithoutProject);
        SwingUtilities.invokeAndWait(ProjectTreePanelTest::testRefreshIconPaints);
        SwingUtilities.invokeAndWait(ProjectTreePanelTest::testSkipNameKeepsDotfiles);
        testRefreshEnabledWhenProjectOpen();
        testRefreshPicksUpNewFile();
        testShowsDotfilesAndSkipsGit();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testRefreshDisabledWithoutProject() {
        ProjectTreePanel tree = new ProjectTreePanel();
        JButton refresh = tree.getRefreshButton();
        assertTrue("refresh button present", refresh != null);
        assertTrue("no text label", refresh.getText() == null || refresh.getText().isEmpty());
        assertTrue("uses refresh icon", refresh.getIcon() instanceof ProjectTreePanel.RefreshIcon);
        assertEqual("tooltip", "Refresh", refresh.getToolTipText());
        assertEqual("icon width", ProjectTreePanel.RefreshIcon.SIZE, refresh.getIcon().getIconWidth());
        assertTrue("disabled without project", !refresh.isEnabled());
        tree.refresh();
        assertTrue("still no project", tree.getProjectRoot() == null);
    }

    private static void testRefreshIconPaints() {
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
        assertTrue("icon draws pixels", painted);
    }

    private static void testRefreshEnabledWhenProjectOpen() throws Exception {
        Path root = Files.createTempDirectory("lide-tree-refresh-enable");
        try {
            SwingUtilities.invokeAndWait(() -> {
                ProjectTreePanel tree = new ProjectTreePanel();
                tree.openDirectory(root);
                assertTrue("enabled with project", tree.getRefreshButton().isEnabled());
            });
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testRefreshPicksUpNewFile() throws Exception {
        Path root = Files.createTempDirectory("lide-tree-refresh-files");
        try {
            Files.writeString(root.resolve("first.txt"), "one");
            ProjectTreePanel tree = new ProjectTreePanel();
            SwingUtilities.invokeAndWait(() -> tree.openDirectory(root));
            assertTrue("first.txt after open", waitForChild(tree, "first.txt", 5000));
            assertTrue("second.txt not yet", !tree.rootChildNames().contains("second.txt"));

            Files.writeString(root.resolve("second.txt"), "two");
            SwingUtilities.invokeAndWait(() -> tree.getRefreshButton().doClick());
            assertTrue("second.txt after refresh", waitForChild(tree, "second.txt", 5000));
            assertTrue("first.txt still there", tree.rootChildNames().contains("first.txt"));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testSkipNameKeepsDotfiles() {
        assertTrue("sdkmanrc shown", !ProjectTreePanel.skipName(".sdkmanrc"));
        assertTrue("gitignore shown", !ProjectTreePanel.skipName(".gitignore"));
        assertTrue("sdkman dir shown", !ProjectTreePanel.skipName(".sdkman"));
        assertTrue("editorconfig shown", !ProjectTreePanel.skipName(".editorconfig"));
        assertTrue("skips .git", ProjectTreePanel.skipName(".git"));
        assertTrue("skips .idea", ProjectTreePanel.skipName(".idea"));
        assertTrue("skips out", ProjectTreePanel.skipName("out"));
        assertTrue("skips build", ProjectTreePanel.skipName("build"));
        assertTrue("null not skipped as a name match", !ProjectTreePanel.skipName(null));
        assertTrue("plain file shown", !ProjectTreePanel.skipName("Main.java"));
    }

    private static void testShowsDotfilesAndSkipsGit() throws Exception {
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
            assertTrue(".sdkmanrc visible", waitForChild(tree, ".sdkmanrc", 5000));
            assertTrue(".gitignore visible", tree.rootChildNames().contains(".gitignore"));
            assertTrue("README visible", tree.rootChildNames().contains("README.md"));
            assertTrue(".git hidden", !tree.rootChildNames().contains(".git"));
            assertTrue("out hidden", !tree.rootChildNames().contains("out"));
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

    private ProjectTreePanelTest() {
    }
}
