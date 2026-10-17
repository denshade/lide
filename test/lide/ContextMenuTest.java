package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
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

    @BeforeEach
    void requireDisplay() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "headless environment cannot run ContextMenuTest");
    }

    @Test
    void testTabContextMenuItems() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testTabContextMenuItems);
            return;
        }
        EditorTabPane pane = new EditorTabPane();
        pane.openUntitled("a.txt", "a", Language.PLAIN);
        pane.openUntitled("b.txt", "b", Language.PLAIN);
        JPopupMenu menu = pane.createTabContextMenu(0);
        List<String> labels = menuItemTexts(menu);
        assertEquals(List.of("Close", "Close Others", "Close All", "Copy Path"), labels, "tab menu items");
        assertTrue(findItem(menu, "Close Others").isEnabled(), "Close Others enabled with 2 tabs");
        assertTrue(!findItem(menu, "Copy Path").isEnabled(), "Copy Path disabled for untitled");
    }

    @Test
    void testCloseOthers() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testCloseOthers);
            return;
        }
        EditorTabPane pane = new EditorTabPane();
        pane.openUntitled("a.txt", "a", Language.PLAIN);
        pane.openUntitled("b.txt", "b", Language.PLAIN);
        pane.openUntitled("c.txt", "c", Language.PLAIN);
        CodeEditor keep = pane.getActiveEditor();
        pane.closeOtherTabs(keep);
        assertEquals(1, pane.getTabCount(), "tabs after close others");
        assertEquals(keep, pane.getActiveEditor(), "kept editor");
    }

    @Test
    void testCloseAll() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testCloseAll);
            return;
        }
        EditorTabPane pane = new EditorTabPane();
        pane.openUntitled("a.txt", "a", Language.PLAIN);
        pane.openUntitled("b.txt", "b", Language.PLAIN);
        pane.closeAllTabs();
        assertEquals(0, pane.getTabCount(), "tabs after close all");
    }

    @Test
    void testProjectTreeFileMenu() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testProjectTreeFileMenu);
            return;
        }
        ProjectTreePanel tree = new ProjectTreePanel();
        AtomicReference<Path> opened = new AtomicReference<>();
        tree.setOpenFileHandler(opened::set);

        Path file = Path.of("src", "Demo.java");
        DefaultMutableTreeNode node =
                new DefaultMutableTreeNode(new ProjectTreePanel.FileNode(file, false));
        JPopupMenu menu = tree.createContextMenu(node).orElseThrow();
        assertEquals(List.of("Open", "Rename…", "Copy Path"), menuItemTexts(menu), "file menu items");
        findItem(menu, "Open").doClick();
        assertEquals(file, opened.get(), "open handler path");
    }

    @Test
    void testProjectTreeTestFileMenu() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testProjectTreeTestFileMenu);
            return;
        }
        ProjectTreePanel tree = new ProjectTreePanel();
        AtomicReference<Path> ran = new AtomicReference<>();
        tree.setRunTestHandler(ran::set);

        Path file = Path.of("test", "DemoTest.java");
        DefaultMutableTreeNode node =
                new DefaultMutableTreeNode(new ProjectTreePanel.FileNode(file, false));
        JPopupMenu menu = tree.createContextMenu(node).orElseThrow();
        assertEquals(List.of("Open", "Run Test", "Rename…", "Copy Path"), menuItemTexts(menu), "test file menu items");
        findItem(menu, "Run Test").doClick();
        assertEquals(file, ran.get(), "run test handler path");
    }

    @Test
    void testProjectTreeDirectoryMenu() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testProjectTreeDirectoryMenu);
            return;
        }
        ProjectTreePanel tree = new ProjectTreePanel();
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(
                new ProjectTreePanel.FileNode(Path.of("src"), true));
        JPopupMenu menu = tree.createContextMenu(node).orElseThrow();
        assertEquals(List.of("New File…", "Rename…", "Copy Path", "Refresh"), menuItemTexts(menu), "dir menu items");
    }

    @Test
    void testProjectTreeNewFileHandler() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testProjectTreeNewFileHandler);
            return;
        }
        ProjectTreePanel tree = new ProjectTreePanel();
        AtomicReference<Path> createdIn = new AtomicReference<>();
        tree.setNewFileHandler(createdIn::set);

        Path dir = Path.of("src");
        DefaultMutableTreeNode node =
                new DefaultMutableTreeNode(new ProjectTreePanel.FileNode(dir, true));
        JPopupMenu menu = tree.createContextMenu(node).orElseThrow();
        findItem(menu, "New File…").doClick();
        assertEquals(dir, createdIn.get(), "new file handler path");
    }

    @Test
    void testProjectTreeRenameAndCopyPathHandlers() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testProjectTreeRenameAndCopyPathHandlers);
            return;
        }
        ProjectTreePanel tree = new ProjectTreePanel();
        AtomicReference<Path> renamed = new AtomicReference<>();
        AtomicReference<Path> copied = new AtomicReference<>();
        tree.setRenameHandler(renamed::set);
        tree.setCopyPathHandler(copied::set);

        Path file = Path.of("src", "Demo.java");
        DefaultMutableTreeNode node =
                new DefaultMutableTreeNode(new ProjectTreePanel.FileNode(file, false));
        JPopupMenu menu = tree.createContextMenu(node).orElseThrow();
        findItem(menu, "Rename…").doClick();
        findItem(menu, "Copy Path").doClick();
        assertEquals(file, renamed.get(), "rename handler path");
        assertEquals(file, copied.get(), "copy path handler path");
    }

    @Test
    void testProjectRootOmitsRename() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testProjectRootOmitsRename);
            return;
        }
        ProjectTreePanel tree = new ProjectTreePanel();
        Path root = Path.of("proj").toAbsolutePath().normalize();
        tree.openDirectory(root);
        DefaultMutableTreeNode node =
                new DefaultMutableTreeNode(new ProjectTreePanel.FileNode(root, true));
        JPopupMenu menu = tree.createContextMenu(node).orElseThrow();
        assertEquals(List.of("New File…", "Copy Path", "Refresh"), menuItemTexts(menu), "root menu items");
        assertTrue(findItem(menu, "Rename…") == null, "no rename on project root");
    }

    @Test
    void testProjectTreeLabelHasNoMenu() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testProjectTreeLabelHasNoMenu);
            return;
        }
        ProjectTreePanel tree = new ProjectTreePanel();
        DefaultMutableTreeNode node = new DefaultMutableTreeNode("Loading…");
        assertTrue(tree.createContextMenu(node).isEmpty(), "label node has no menu");
    }

    @Test
    void testClickInactiveTabSelectsIt() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testClickInactiveTabSelectsIt);
            return;
        }
        EditorTabPane pane = new EditorTabPane();
        pane.openUntitled("a.txt", "a", Language.PLAIN);
        CodeEditor first = pane.getActiveEditor();
        pane.openUntitled("b.txt", "b", Language.PLAIN);
        assertEquals(1, pane.getSelectedTabIndex(), "second tab active");

        java.awt.Component header = pane.getTabHeaderAt(0);
        assertTrue(header != null, "tab header present");
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

        assertEquals(0, pane.getSelectedTabIndex(), "inactive tab selected");
        assertEquals(first, pane.getActiveEditor(), "first editor shown");
    }

    @Test
    void testTabCloseUsesXIcon() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testTabCloseUsesXIcon);
            return;
        }
        EditorTabPane pane = new EditorTabPane();
        pane.openUntitled("a.txt", "a", Language.PLAIN);
        java.awt.Component header = pane.getTabHeaderAt(0);
        assertTrue(header instanceof javax.swing.JPanel, "header is panel");
        javax.swing.JButton close = null;
        for (java.awt.Component child : ((javax.swing.JPanel) header).getComponents()) {
            if (child instanceof javax.swing.JButton button) {
                close = button;
                break;
            }
        }
        assertTrue(close != null, "close button present");
        assertTrue(close.getIcon() instanceof EditorTabPane.TabCloseIcon, "close uses icon");
        assertTrue(close.getText() == null || close.getText().isEmpty(), "close has no text label");
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

}

/**
 * Runs Swing test bodies on the event dispatch thread and rethrows failures
 * on the JUnit thread. Lives in a {@code *Test} source file because Ladle
 * compiles those for {@code ladle test}.
 */
final class SwingSupport {
    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    private SwingSupport() {
    }

    static void onEdt(ThrowingRunnable action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    action.run();
                } catch (Throwable thrown) {
                    failure.set(thrown);
                }
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw ex;
        }
        Throwable thrown = failure.get();
        if (thrown instanceof Exception exception) {
            throw exception;
        }
        if (thrown instanceof Error error) {
            throw error;
        }
    }
}
