package lide;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Tests for right-click context menus.
 */
public final class ContextMenuTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: headless environment cannot run Swing context-menu tests");
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            testTabContextMenuItems();
            testCloseOthers();
            testCloseAll();
            testProjectTreeFileMenu();
            testProjectTreeDirectoryMenu();
            testProjectTreeNewFileHandler();
            testProjectTreeRenameAndCopyPathHandlers();
            testProjectRootOmitsRename();
            testClickInactiveTabSelectsIt();
            testTabCloseUsesXIcon();
        });
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testTabContextMenuItems() {
        EditorTabPane pane = new EditorTabPane();
        pane.openUntitled("a.txt", "a", Language.PLAIN);
        pane.openUntitled("b.txt", "b", Language.PLAIN);
        JPopupMenu menu = pane.createTabContextMenu(0);
        List<String> labels = menuItemTexts(menu);
        assertEqual("tab menu items", List.of("Close", "Close Others", "Close All", "Copy Path"), labels);
        assertTrue("Close Others enabled with 2 tabs",
                findItem(menu, "Close Others").isEnabled());
        assertTrue("Copy Path disabled for untitled", !findItem(menu, "Copy Path").isEnabled());
    }

    private static void testCloseOthers() {
        EditorTabPane pane = new EditorTabPane();
        pane.openUntitled("a.txt", "a", Language.PLAIN);
        pane.openUntitled("b.txt", "b", Language.PLAIN);
        pane.openUntitled("c.txt", "c", Language.PLAIN);
        CodeEditor keep = pane.getActiveEditor();
        pane.closeOtherTabs(keep);
        assertEqual("tabs after close others", 1, pane.getTabCount());
        assertEqual("kept editor", keep, pane.getActiveEditor());
    }

    private static void testCloseAll() {
        EditorTabPane pane = new EditorTabPane();
        pane.openUntitled("a.txt", "a", Language.PLAIN);
        pane.openUntitled("b.txt", "b", Language.PLAIN);
        pane.closeAllTabs();
        assertEqual("tabs after close all", 0, pane.getTabCount());
    }

    private static void testProjectTreeFileMenu() {
        ProjectTreePanel tree = new ProjectTreePanel();
        AtomicReference<Path> opened = new AtomicReference<>();
        tree.setOpenFileHandler(opened::set);

        Path file = Path.of("src", "Demo.java");
        DefaultMutableTreeNode node =
                new DefaultMutableTreeNode(new ProjectTreePanel.FileNode(file, false));
        JPopupMenu menu = tree.createContextMenu(node);
        assertEqual("file menu items", List.of("Open", "Rename…", "Copy Path"), menuItemTexts(menu));
        findItem(menu, "Open").doClick();
        assertEqual("open handler path", file, opened.get());
    }

    private static void testProjectTreeDirectoryMenu() {
        ProjectTreePanel tree = new ProjectTreePanel();
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(
                new ProjectTreePanel.FileNode(Path.of("src"), true));
        JPopupMenu menu = tree.createContextMenu(node);
        assertEqual("dir menu items",
                List.of("New File…", "Rename…", "Copy Path", "Refresh"),
                menuItemTexts(menu));
    }

    private static void testProjectTreeNewFileHandler() {
        ProjectTreePanel tree = new ProjectTreePanel();
        AtomicReference<Path> createdIn = new AtomicReference<>();
        tree.setNewFileHandler(createdIn::set);

        Path dir = Path.of("src");
        DefaultMutableTreeNode node =
                new DefaultMutableTreeNode(new ProjectTreePanel.FileNode(dir, true));
        JPopupMenu menu = tree.createContextMenu(node);
        findItem(menu, "New File…").doClick();
        assertEqual("new file handler path", dir, createdIn.get());
    }

    private static void testProjectTreeRenameAndCopyPathHandlers() {
        ProjectTreePanel tree = new ProjectTreePanel();
        AtomicReference<Path> renamed = new AtomicReference<>();
        AtomicReference<Path> copied = new AtomicReference<>();
        tree.setRenameHandler(renamed::set);
        tree.setCopyPathHandler(copied::set);

        Path file = Path.of("src", "Demo.java");
        DefaultMutableTreeNode node =
                new DefaultMutableTreeNode(new ProjectTreePanel.FileNode(file, false));
        JPopupMenu menu = tree.createContextMenu(node);
        findItem(menu, "Rename…").doClick();
        findItem(menu, "Copy Path").doClick();
        assertEqual("rename handler path", file, renamed.get());
        assertEqual("copy path handler path", file, copied.get());
    }

    private static void testProjectRootOmitsRename() {
        ProjectTreePanel tree = new ProjectTreePanel();
        Path root = Path.of("proj").toAbsolutePath().normalize();
        tree.openDirectory(root);
        DefaultMutableTreeNode node =
                new DefaultMutableTreeNode(new ProjectTreePanel.FileNode(root, true));
        JPopupMenu menu = tree.createContextMenu(node);
        assertEqual("root menu items",
                List.of("New File…", "Copy Path", "Refresh"),
                menuItemTexts(menu));
        assertTrue("no rename on project root", findItem(menu, "Rename…") == null);
    }

    private static void testClickInactiveTabSelectsIt() {
        EditorTabPane pane = new EditorTabPane();
        pane.openUntitled("a.txt", "a", Language.PLAIN);
        CodeEditor first = pane.getActiveEditor();
        pane.openUntitled("b.txt", "b", Language.PLAIN);
        assertEqual("second tab active", 1, pane.getSelectedTabIndex());

        java.awt.Component header = pane.getTabHeaderAt(0);
        assertTrue("tab header present", header != null);
        header.dispatchEvent(new java.awt.event.MouseEvent(
                header,
                java.awt.event.MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0,
                2,
                2,
                1,
                false,
                java.awt.event.MouseEvent.BUTTON1));

        assertEqual("inactive tab selected", 0, pane.getSelectedTabIndex());
        assertEqual("first editor shown", first, pane.getActiveEditor());
    }

    private static void testTabCloseUsesXIcon() {
        EditorTabPane pane = new EditorTabPane();
        pane.openUntitled("a.txt", "a", Language.PLAIN);
        java.awt.Component header = pane.getTabHeaderAt(0);
        assertTrue("header is panel", header instanceof javax.swing.JPanel);
        javax.swing.JButton close = null;
        for (java.awt.Component child : ((javax.swing.JPanel) header).getComponents()) {
            if (child instanceof javax.swing.JButton button) {
                close = button;
                break;
            }
        }
        assertTrue("close button present", close != null);
        assertTrue("close uses icon", close.getIcon() instanceof EditorTabPane.TabCloseIcon);
        assertTrue("close has no text label", close.getText() == null || close.getText().isEmpty());
    }

    private static List<String> menuItemTexts(JPopupMenu menu) {
        List<String> labels = new ArrayList<>();
        for (Component component : menu.getComponents()) {
            if (component instanceof JMenuItem item) {
                labels.add(item.getText());
            }
        }
        return labels;
    }

    private static JMenuItem findItem(JPopupMenu menu, String text) {
        for (Component component : menu.getComponents()) {
            if (component instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
        }
        return null;
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

    private ContextMenuTest() {
    }
}
