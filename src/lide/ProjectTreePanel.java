package lide;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * Project file tree for an opened directory.
 */
public final class ProjectTreePanel extends JPanel {
    private static final Set<String> SKIP_NAMES = Set.of(
            "out", "build", "target", "node_modules", ".git", ".idea", ".svn", ".hg");

    private final JTree tree;
    private final DefaultTreeModel model;
    private final DefaultMutableTreeNode rootNode;
    private Consumer<Path> openFileHandler = path -> {
    };
    private Consumer<Path> newFileHandler = path -> {
    };
    private Consumer<Path> renameHandler = path -> {
    };
    private Consumer<Path> copyPathHandler = PathClipboard::copy;
    private Consumer<Path> runTestHandler = path -> {
    };
    private Path projectRoot;
    private int loadGeneration;

    public ProjectTreePanel() {
        super(new BorderLayout());
        setBackground(IdeTheme.BG_TREE);

        rootNode = new DefaultMutableTreeNode(new LabelNode("No Project"));
        model = new DefaultTreeModel(rootNode);
        tree = new JTree(model);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setBackground(IdeTheme.BG_TREE);
        tree.setForeground(IdeTheme.FG);
        tree.setFont(IdeTheme.TREE_FONT);
        tree.setRowHeight(22);
        tree.setCellRenderer(new ProjectTreeRenderer());

        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                DefaultMutableTreeNode node =
                        (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                ensureChildrenLoaded(node);
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
            }
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || e.isPopupTrigger()) {
                    return;
                }
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }
                DefaultMutableTreeNode node =
                        (DefaultMutableTreeNode) path.getLastPathComponent();
                if (node.getUserObject() instanceof FileNode fileNode && !fileNode.directory()) {
                    openFileHandler.accept(fileNode.path());
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowContextMenu(e);
            }
        });

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(IdeTheme.BG_TREE);
        add(scroll, BorderLayout.CENTER);
    }

    private void maybeShowContextMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }
        tree.setSelectionPath(path);
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        createContextMenu(node).ifPresent(menu -> menu.show(tree, e.getX(), e.getY()));
    }

    Optional<JPopupMenu> createContextMenu(DefaultMutableTreeNode node) {
        if (!(node.getUserObject() instanceof FileNode fileNode)) {
            return Optional.empty();
        }
        JPopupMenu menu = new JPopupMenu();
        if (!fileNode.directory()) {
            JMenuItem open = new JMenuItem("Open");
            open.addActionListener(e -> openFileHandler.accept(fileNode.path()));
            menu.add(open);
            if (TestNavigator.isTestJavaFile(fileNode.path())) {
                JMenuItem runTest = new JMenuItem("Run Test");
                runTest.addActionListener(e -> runTestHandler.accept(fileNode.path()));
                menu.add(runTest);
            }
        } else {
            JMenuItem newFile = new JMenuItem("New File…");
            newFile.addActionListener(e -> newFileHandler.accept(fileNode.path()));
            menu.add(newFile);
        }
        if (!isProjectRoot(fileNode.path())) {
            JMenuItem rename = new JMenuItem("Rename…");
            rename.addActionListener(e -> renameHandler.accept(fileNode.path()));
            menu.add(rename);
        }
        JMenuItem copyPath = new JMenuItem("Copy Path");
        copyPath.addActionListener(e -> copyPathHandler.accept(fileNode.path()));
        menu.add(copyPath);
        if (fileNode.directory()) {
            JMenuItem refresh = new JMenuItem("Refresh");
            refresh.addActionListener(e -> refreshNode(node));
            menu.add(refresh);
        }
        return Optional.of(menu);
    }

    private void refreshNode(DefaultMutableTreeNode node) {
        node.removeAllChildren();
        node.add(new DefaultMutableTreeNode(new LabelNode("Loading…")));
        model.nodeStructureChanged(node);
        ensureChildrenLoaded(node);
    }

    public void setOpenFileHandler(Consumer<Path> openFileHandler) {
        this.openFileHandler = openFileHandler;
    }

    public void setNewFileHandler(Consumer<Path> newFileHandler) {
        this.newFileHandler = newFileHandler;
    }

    public void setRenameHandler(Consumer<Path> renameHandler) {
        this.renameHandler = renameHandler;
    }

    public void setCopyPathHandler(Consumer<Path> copyPathHandler) {
        this.copyPathHandler = copyPathHandler;
    }

    public void setRunTestHandler(Consumer<Path> runTestHandler) {
        this.runTestHandler = runTestHandler;
    }

    boolean isProjectRoot(Path path) {
        if (projectRoot == null || path == null) {
            return false;
        }
        return projectRoot.toAbsolutePath().normalize()
                .equals(path.toAbsolutePath().normalize());
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    /**
     * Directory that should receive a new file: the selected folder, the parent
     * of a selected file, or the project root when nothing is selected.
     */
    public Path getSelectedDirectory() {
        if (projectRoot == null) {
            return null;
        }
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return projectRoot;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (!(node.getUserObject() instanceof FileNode fileNode)) {
            return projectRoot;
        }
        if (fileNode.directory()) {
            return fileNode.path();
        }
        Path parent = fileNode.path().getParent();
        return parent != null ? parent : projectRoot;
    }

    void refreshDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        DefaultMutableTreeNode node = findDirectoryNode(rootNode, directory.toAbsolutePath().normalize());
        if (node != null) {
            refreshNode(node);
        }
    }

    private static DefaultMutableTreeNode findDirectoryNode(DefaultMutableTreeNode node, Path target) {
        if (node.getUserObject() instanceof FileNode fileNode
                && fileNode.directory()
                && fileNode.path().toAbsolutePath().normalize().equals(target)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode found =
                    findDirectoryNode((DefaultMutableTreeNode) node.getChildAt(i), target);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public void openDirectory(Path directory) {
        Path root = directory.toAbsolutePath().normalize();
        this.projectRoot = root;
        int generation = ++loadGeneration;

        rootNode.setUserObject(new FileNode(root, true));
        rootNode.removeAllChildren();
        rootNode.add(new DefaultMutableTreeNode(new LabelNode("Loading…")));
        model.reload(rootNode);
        tree.expandPath(new TreePath(rootNode.getPath()));

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<List<DefaultMutableTreeNode>, Void>() {
            @Override
            protected List<DefaultMutableTreeNode> doInBackground() {
                return listChildren(root);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                if (generation != loadGeneration) {
                    return;
                }
                try {
                    replaceChildren(rootNode, get());
                    tree.expandPath(new TreePath(rootNode.getPath()));
                } catch (Exception ex) {
                    AppLog.exception("Could not load project tree root " + root, ex);
                    replaceChildren(rootNode, List.of(
                            new DefaultMutableTreeNode(new LabelNode(
                                    "Failed to load: " + ex.getMessage()))));
                }
            }
        }.execute();
    }

    private void ensureChildrenLoaded(DefaultMutableTreeNode node) {
        if (!(node.getUserObject() instanceof FileNode fileNode) || !fileNode.directory()) {
            return;
        }
        if (!needsLoad(node)) {
            return;
        }

        // Load subdirectory listings off the EDT so expand stays responsive.
        final Path dir = fileNode.path();
        new SwingWorker<List<DefaultMutableTreeNode>, Void>() {
            @Override
            protected List<DefaultMutableTreeNode> doInBackground() {
                return listChildren(dir);
            }

            @Override
            protected void done() {
                if (!(node.getUserObject() instanceof FileNode current)
                        || !current.path().equals(dir)
                        || !needsLoad(node)) {
                    return;
                }
                try {
                    replaceChildren(node, get());
                    tree.expandPath(new TreePath(node.getPath()));
                } catch (Exception ex) {
                    AppLog.exception("Could not load project tree folder " + dir, ex);
                    replaceChildren(node, List.of(
                            new DefaultMutableTreeNode(new LabelNode(
                                    "Failed to load: " + ex.getMessage()))));
                }
            }
        }.execute();
    }

    private static boolean needsLoad(DefaultMutableTreeNode node) {
        if (node.getChildCount() == 0) {
            return true;
        }
        if (node.getChildCount() == 1) {
            Object child = ((DefaultMutableTreeNode) node.getFirstChild()).getUserObject();
            return child instanceof LabelNode;
        }
        return false;
    }

    private void replaceChildren(
            DefaultMutableTreeNode parent, List<DefaultMutableTreeNode> children) {
        parent.removeAllChildren();
        for (DefaultMutableTreeNode child : children) {
            parent.add(child);
        }
        model.nodeStructureChanged(parent);
    }

    private static List<DefaultMutableTreeNode> listChildren(Path dir) {
        List<Entry> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                Path namePath = child.getFileName();
                if (namePath == null) {
                    continue;
                }
                String name = namePath.toString();
                if (name.startsWith(".") || SKIP_NAMES.contains(name)) {
                    continue;
                }
                try {
                    BasicFileAttributes attrs = Files.readAttributes(
                            child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    // Skip reparse points / junctions that often hang on Windows.
                    if (attrs.isSymbolicLink() || attrs.isOther()) {
                        continue;
                    }
                    entries.add(new Entry(child, attrs.isDirectory()));
                } catch (IOException ex) {
                    AppLog.exception("Could not read attributes for " + child, ex);
                }
            }
        } catch (IOException ex) {
            AppLog.exception("Could not list folder " + dir, ex);
            return List.of(new DefaultMutableTreeNode(
                    new LabelNode("Cannot read folder")));
        }

        entries.sort(Comparator
                .comparing((Entry e) -> !e.directory)
                .thenComparing(e -> e.path.getFileName().toString().toLowerCase(Locale.ROOT)));

        List<DefaultMutableTreeNode> result = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            DefaultMutableTreeNode child =
                    new DefaultMutableTreeNode(new FileNode(entry.path, entry.directory));
            if (entry.directory) {
                child.add(new DefaultMutableTreeNode(new LabelNode("Loading…")));
            }
            result.add(child);
        }
        return result;
    }

    private record Entry(Path path, boolean directory) {
    }

    record FileNode(Path path, boolean directory) {
        @Override
        public String toString() {
            Path name = path.getFileName();
            return name != null ? name.toString() : path.toString();
        }
    }

    private record LabelNode(String text) {
        @Override
        public String toString() {
            return text;
        }
    }

    private static final class ProjectTreeRenderer extends DefaultTreeCellRenderer {
        ProjectTreeRenderer() {
            setBackgroundNonSelectionColor(IdeTheme.BG_TREE);
            setBackgroundSelectionColor(IdeTheme.SELECTION);
            setTextNonSelectionColor(IdeTheme.FG);
            setTextSelectionColor(java.awt.Color.WHITE);
            setBorderSelectionColor(IdeTheme.SELECTION);
            setFont(IdeTheme.TREE_FONT);
        }

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean selected, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            setOpaque(true);
            if (!selected) {
                setBackground(IdeTheme.BG_TREE);
            }
            return this;
        }
    }
}
