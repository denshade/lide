package lide;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.RenderingHints;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
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
    private final JButton refreshButton = new JButton(new RefreshIcon());
    private final JButton selectButton = new JButton("Select");
    private final JLabel titleLabel = new JLabel("Project");
    private Consumer<Path> openFileHandler = path -> {
    };
    private Consumer<Path> newFileHandler = path -> {
    };
    private Consumer<Path> renameHandler = path -> {
    };
    private Consumer<Path> deleteHandler = path -> {
    };
    private Consumer<Path> copyPathHandler = PathClipboard::copy;
    private Consumer<Path> runTestHandler = path -> {
    };
    private Supplier<Path> activeFileSupplier = () -> null;
    private final Map<DefaultMutableTreeNode, Integer> loadTokens = new IdentityHashMap<>();
    private final Map<DefaultMutableTreeNode, List<Runnable>> loadCallbacks = new IdentityHashMap<>();
    private int nextLoadToken;
    private Path projectRoot;
    private int loadGeneration;
    private int selectGeneration;

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

        titleLabel.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 12f));
        titleLabel.setForeground(IdeTheme.FG);
        refreshButton.setToolTipText("Refresh");
        refreshButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        refreshButton.setPreferredSize(new Dimension(22, 22));
        refreshButton.setMaximumSize(new Dimension(22, 22));
        refreshButton.setFocusable(false);
        refreshButton.setBorderPainted(false);
        refreshButton.setContentAreaFilled(false);
        refreshButton.setOpaque(false);
        refreshButton.setEnabled(false);
        refreshButton.addActionListener(e -> refresh());

        selectButton.setToolTipText("Select the open file in the project tree");
        selectButton.setFont(IdeTheme.UI_FONT.deriveFont(Font.PLAIN, 12f));
        selectButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
        selectButton.setFocusable(false);
        selectButton.setBorderPainted(false);
        selectButton.setContentAreaFilled(false);
        selectButton.setOpaque(false);
        selectButton.setEnabled(false);
        selectButton.addPropertyChangeListener("enabled", e -> applyToolbarButtonColor(selectButton));
        applyToolbarButtonColor(selectButton);
        selectButton.addActionListener(e -> selectOpenFile());

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(IdeTheme.BG_RAISED);
        toolbar.setBorder(new EmptyBorder(4, 8, 4, 8));
        toolbar.add(titleLabel, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        right.add(selectButton);
        right.add(refreshButton);
        toolbar.add(right, BorderLayout.EAST);

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
        add(toolbar, BorderLayout.NORTH);
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
        if (!fileNode.directory()) {
            JMenuItem delete = new JMenuItem("Delete");
            delete.addActionListener(e -> deleteHandler.accept(fileNode.path()));
            menu.add(delete);
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

    /**
     * Reloads the open project tree from disk. No-op when no directory is open.
     */
    public void refresh() {
        if (projectRoot == null) {
            return;
        }
        openDirectory(projectRoot);
    }

    JButton getRefreshButton() {
        return refreshButton;
    }

    JButton getSelectButton() {
        return selectButton;
    }

    public void setActiveFileSupplier(Supplier<Path> activeFileSupplier) {
        this.activeFileSupplier = activeFileSupplier != null ? activeFileSupplier : () -> null;
    }

    /**
     * Selects the editor's current file in the tree, loading and expanding
     * parent folders until that row is visible.
     */
    void selectOpenFile() {
        selectFile(activeFileSupplier.get());
    }

    void selectFile(Path file) {
        int generation = ++selectGeneration;
        if (projectRoot == null || file == null) {
            return;
        }
        Path target = file.toAbsolutePath().normalize();
        Path root = projectRoot.toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            return;
        }
        reveal(rootNode, target, generation);
    }

    private void reveal(DefaultMutableTreeNode node, Path target, int generation) {
        if (generation != selectGeneration || !(node.getUserObject() instanceof FileNode fileNode)) {
            return;
        }
        Path nodePath = fileNode.path().toAbsolutePath().normalize();
        if (nodePath.equals(target)) {
            TreePath treePath = new TreePath(node.getPath());
            tree.setSelectionPath(treePath);
            tree.scrollPathToVisible(treePath);
            return;
        }
        if (!fileNode.directory() || !target.startsWith(nodePath)) {
            return;
        }
        if (needsLoad(node)) {
            loadChildren(node, () -> {
                if (generation != selectGeneration || needsLoad(node)) {
                    return;
                }
                reveal(node, target, generation);
            });
            return;
        }
        DefaultMutableTreeNode next = findChildToward(node, target);
        if (next == null) {
            return;
        }
        tree.expandPath(new TreePath(node.getPath()));
        reveal(next, target, generation);
    }

    private static DefaultMutableTreeNode findChildToward(DefaultMutableTreeNode node, Path target) {
        if (!(node.getUserObject() instanceof FileNode fileNode)) {
            return null;
        }
        Path nodePath = fileNode.path().toAbsolutePath().normalize();
        Path relative;
        try {
            relative = nodePath.relativize(target.toAbsolutePath().normalize());
        } catch (IllegalArgumentException ex) {
            AppLog.exception("Could not locate " + target + " under " + nodePath, ex);
            return null;
        }
        if (relative.getNameCount() == 0) {
            return null;
        }
        Path nextName = relative.getName(0);
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            if (!(child.getUserObject() instanceof FileNode childNode) || childNode.path().getFileName() == null) {
                continue;
            }
            if (childNode.path().getFileName().equals(nextName)) {
                return child;
            }
        }
        return null;
    }

    Path getSelectedPath() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return null;
        }
        Object user = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (user instanceof FileNode fileNode) {
            return fileNode.path().toAbsolutePath().normalize();
        }
        return null;
    }

    int getSelectedRow() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return -1;
        }
        return tree.getRowForPath(path);
    }

    private static void applyToolbarButtonColor(JButton button) {
        button.setForeground(button.isEnabled() ? IdeTheme.FG : IdeTheme.FG_DISABLED);
    }

    List<String> rootChildNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            Object user = ((DefaultMutableTreeNode) rootNode.getChildAt(i)).getUserObject();
            if (user instanceof FileNode fileNode && fileNode.path().getFileName() != null) {
                names.add(fileNode.path().getFileName().toString());
            }
        }
        return names;
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

    public void setDeleteHandler(Consumer<Path> deleteHandler) {
        this.deleteHandler = deleteHandler;
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
        refreshButton.setEnabled(true);
        selectButton.setEnabled(true);
        applyToolbarButtonColor(selectButton);
        int generation = ++loadGeneration;
        selectGeneration++;
        loadTokens.clear();
        loadCallbacks.clear();

        rootNode.setUserObject(new FileNode(root, true));
        rootNode.removeAllChildren();
        rootNode.add(new DefaultMutableTreeNode(new LabelNode("Loading…")));
        model.reload(rootNode);
        tree.expandPath(new TreePath(rootNode.getPath()));

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        loadChildren(rootNode, () -> {
            if (generation == loadGeneration) {
                setCursor(Cursor.getDefaultCursor());
            }
        });
    }

    private void ensureChildrenLoaded(DefaultMutableTreeNode node) {
        loadChildren(node, null);
    }

    /**
     * Lists {@code node}'s children off the EDT. {@code after} runs on the EDT
     * when this load finishes; callers that find the node still unloaded should stop.
     */
    private void loadChildren(DefaultMutableTreeNode node, Runnable after) {
        if (!(node.getUserObject() instanceof FileNode fileNode) || !fileNode.directory()) {
            return;
        }
        if (!needsLoad(node)) {
            if (after != null) {
                after.run();
            }
            return;
        }
        if (after != null) {
            loadCallbacks.computeIfAbsent(node, key -> new ArrayList<>()).add(after);
        }
        if (loadTokens.containsKey(node)) {
            return;
        }

        Integer token = Integer.valueOf(++nextLoadToken);
        loadTokens.put(node, token);
        final Path dir = fileNode.path();
        final int generation = loadGeneration;
        new SwingWorker<List<DefaultMutableTreeNode>, Void>() {
            @Override
            protected List<DefaultMutableTreeNode> doInBackground() {
                return listChildren(dir);
            }

            @Override
            protected void done() {
                if (!token.equals(loadTokens.get(node))) {
                    return;
                }
                loadTokens.remove(node);
                List<Runnable> callbacks = loadCallbacks.remove(node);
                if (generation != loadGeneration) {
                    return;
                }
                if (!(node.getUserObject() instanceof FileNode current)
                        || !current.path().equals(dir)) {
                    runCallbacks(callbacks);
                    return;
                }
                if (needsLoad(node)) {
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
                runCallbacks(callbacks);
            }
        }.execute();
    }

    private static void runCallbacks(List<Runnable> callbacks) {
        if (callbacks == null) {
            return;
        }
        for (Runnable callback : callbacks) {
            try {
                callback.run();
            } catch (RuntimeException ex) {
                AppLog.exception("Project tree load callback failed", ex);
            }
        }
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

    static boolean skipName(String name) {
        return name != null && SKIP_NAMES.contains(name);
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
                if (skipName(name)) {
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

    /**
     * Renders one tree row. {@code leaf} is false for a directory.
     */
    Component renderCell(Object value, boolean selected, boolean leaf) {
        return tree.getCellRenderer().getTreeCellRendererComponent(
                tree, value, selected, !leaf, leaf, 0, false);
    }

    Object projectRootValue() {
        return rootNode;
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
            if (selected) {
                setBackground(IdeTheme.SELECTION);
                setForeground(java.awt.Color.WHITE);
            } else {
                setBackground(IdeTheme.BG_TREE);
                setForeground(IdeTheme.FG);
            }
            setOpaque(true);
            return this;
        }

        @Override
        public void paint(Graphics g) {
            // Fill the whole row first. The label UI then paints getBackground()
            // when opaque, which used to cover a selected directory with white
            // and hide the white text.
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            boolean opaque = isOpaque();
            setOpaque(false);
            super.paint(g);
            setOpaque(opaque);
        }
    }

    /**
     * Circular-arrow glyph for the project tree refresh button.
     */
    static final class RefreshIcon implements Icon {
        static final int SIZE = 14;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean on = c == null || c.isEnabled();
                g2.setColor(on ? IdeTheme.FG_BRIGHT : IdeTheme.FG_DISABLED);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int pad = 1;
                int d = SIZE - pad * 2 - 1;
                g2.drawArc(x + pad, y + pad + 1, d, d, 45, 270);
                int[] xs = {x + SIZE - 2, x + SIZE - 7, x + SIZE - 2};
                int[] ys = {y + 1, y + 1, y + 6};
                g2.fillPolygon(xs, ys, 3);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
