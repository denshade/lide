package lide;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;

/**
 * Tabbed editor area — one tab per open file.
 */
public final class EditorTabPane extends JPanel {
    private final JTabbedPane tabs = new JTabbedPane();
    private final Map<Path, CodeEditor> openEditors = new HashMap<>();
    private final Map<Path, BinaryViewer> openBinary = new HashMap<>();
    private final JLabel emptyLabel;
    private final FindBar findBar = new FindBar();
    private final JPanel contentHost = new JPanel(new BorderLayout());
    private Runnable statusUpdater = () -> {
    };
    private java.util.function.Supplier<Path> projectRootSupplier = () -> null;

    public EditorTabPane() {
        super(new BorderLayout());
        setBackground(IdeTheme.BG);

        emptyLabel = new JLabel(
                "<html><div style='text-align:center;color:#808080'>"
                        + "<div style='font-size:18px;margin-bottom:8px'>Lide</div>"
                        + "<div>Open a directory (File → Open Directory), then File → New File or double-click a file.</div>"
                        + "</div></html>",
                SwingConstants.CENTER);
        emptyLabel.setFont(IdeTheme.UI_FONT);
        emptyLabel.setForeground(IdeTheme.FG_DIM);
        emptyLabel.setBackground(IdeTheme.BG_EDITOR);
        emptyLabel.setOpaque(true);

        tabs.setFont(IdeTheme.UI_FONT);
        tabs.addChangeListener(e -> statusUpdater.run());
        tabs.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowTabContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowTabContextMenu(e);
            }
        });

        findBar.setOnNext(this::findNext);
        findBar.setOnPrevious(this::findPrevious);
        findBar.setOnQueryChanged(this::findFromQueryChange);
        findBar.setOnClose(this::hideFind);

        contentHost.setBackground(IdeTheme.BG);
        add(findBar, BorderLayout.NORTH);
        add(contentHost, BorderLayout.CENTER);
        showEmpty();
    }

    public void setStatusUpdater(Runnable statusUpdater) {
        this.statusUpdater = statusUpdater;
    }

    public void setProjectRootSupplier(java.util.function.Supplier<Path> projectRootSupplier) {
        this.projectRootSupplier = projectRootSupplier;
    }

    public void openFile(Path path) {
        openFile(path, -1);
    }

    /**
     * Opens {@code path} and selects {@code length} characters starting at {@code offset}.
     */
    public void openMatch(Path path, int offset, int length) {
        openFile(path, offset);
        Path normalized = path.toAbsolutePath().normalize();
        CodeEditor editor = openEditors.get(normalized);
        if (editor != null && length > 0) {
            editor.selectRange(offset, offset + length);
            editor.getTextPane().requestFocusInWindow();
        }
    }

    public void openFile(Path path, int caretOffset) {
        Path normalized = path.toAbsolutePath().normalize();
        CodeEditor existingEditor = openEditors.get(normalized);
        if (existingEditor != null) {
            tabs.setSelectedComponent(existingEditor);
            showTabs();
            if (caretOffset >= 0) {
                existingEditor.goToOffset(caretOffset);
            }
            statusUpdater.run();
            return;
        }
        BinaryViewer existingBinary = openBinary.get(normalized);
        if (existingBinary != null) {
            tabs.setSelectedComponent(existingBinary);
            showTabs();
            statusUpdater.run();
            return;
        }

        byte[] bytes;
        try {
            bytes = java.nio.file.Files.readAllBytes(normalized);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not open file:\n" + ex.getMessage(),
                    "Open Failed",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (BinaryDetector.isBinary(bytes)) {
            openBinaryViewer(normalized, bytes);
            return;
        }

        CodeEditor editor = new CodeEditor();
        configureEditor(editor);
        try {
            editor.openFile(normalized);
        } catch (Exception ex) {
            // UTF-8 decode or other text load failure — fall back to binary view.
            if (looksLikeEncodingFailure(ex)) {
                openBinaryViewer(normalized, bytes);
                return;
            }
            JOptionPane.showMessageDialog(
                    this,
                    "Could not open file:\n" + ex.getMessage(),
                    "Open Failed",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        openEditors.put(normalized, editor);
        showTabs();
        tabs.addTab(editor.getTitle(), editor);
        int index = tabs.indexOfComponent(editor);
        tabs.setTabComponentAt(index, new TabHeader(editor.getTitle(), () -> closeTab(editor), this));
        tabs.setSelectedComponent(editor);
        if (caretOffset >= 0) {
            editor.goToOffset(caretOffset);
        }
        statusUpdater.run();
    }

    private void openBinaryViewer(Path normalized, byte[] bytes) {
        hideFind();
        BinaryViewer viewer = new BinaryViewer();
        viewer.setContent(normalized, bytes);
        openBinary.put(normalized, viewer);
        showTabs();
        tabs.addTab(viewer.getTitle(), viewer);
        int index = tabs.indexOfComponent(viewer);
        tabs.setTabComponentAt(index, new TabHeader(viewer.getTitle(), () -> closeBinaryTab(viewer), this));
        tabs.setSelectedComponent(viewer);
        statusUpdater.run();
    }

    static boolean looksLikeEncodingFailure(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof java.nio.charset.CharacterCodingException
                    || t instanceof java.io.UncheckedIOException) {
                return true;
            }
            String message = t.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("malformed") || lower.contains("unmappable")
                        || lower.contains("input length") || lower.contains("utf")) {
                    return true;
                }
            }
        }
        return false;
    }

    public void navigateTo(ClassNavigator.Target target) {
        openFile(target.path(), target.caretOffset());
    }

    private void configureEditor(CodeEditor editor) {
        editor.setProjectRootSupplier(projectRootSupplier);
        editor.setNavigateHandler(this::navigateTo);
        editor.setDirtyListener(() -> {
            int index = tabs.indexOfComponent(editor);
            if (index >= 0) {
                Component tab = tabs.getTabComponentAt(index);
                if (tab instanceof TabHeader header) {
                    header.setTitle(editor.getTitle());
                } else {
                    tabs.setTitleAt(index, editor.getTitle());
                }
            }
            statusUpdater.run();
        });
    }

    public CodeEditor getActiveEditor() {
        Component selected = tabs.getSelectedComponent();
        if (selected instanceof CodeEditor editor) {
            return editor;
        }
        return null;
    }

    public BinaryViewer getActiveBinaryViewer() {
        Component selected = tabs.getSelectedComponent();
        if (selected instanceof BinaryViewer viewer) {
            return viewer;
        }
        return null;
    }

    public boolean hasActiveEditor() {
        return getActiveEditor() != null;
    }

    public boolean hasActiveFileTab() {
        Component selected = tabs.getSelectedComponent();
        return selected instanceof CodeEditor || selected instanceof BinaryViewer;
    }

    public boolean undoActive() {
        CodeEditor editor = getActiveEditor();
        if (editor == null || !editor.canUndo()) {
            return false;
        }
        editor.undo();
        return true;
    }

    public boolean redoActive() {
        CodeEditor editor = getActiveEditor();
        if (editor == null || !editor.canRedo()) {
            return false;
        }
        editor.redo();
        return true;
    }

    public boolean copyActive() {
        CodeEditor editor = getActiveEditor();
        if (editor == null) {
            return false;
        }
        editor.copy();
        return true;
    }

    public boolean pasteActive() {
        CodeEditor editor = getActiveEditor();
        if (editor == null) {
            return false;
        }
        editor.paste();
        return true;
    }

    public boolean canUndoActive() {
        CodeEditor editor = getActiveEditor();
        return editor != null && editor.canUndo();
    }

    public boolean canRedoActive() {
        CodeEditor editor = getActiveEditor();
        return editor != null && editor.canRedo();
    }

    public void showFind() {
        CodeEditor editor = getActiveEditor();
        if (editor == null) {
            return;
        }
        String selected = editor.getSelectedText();
        if (selected != null && !selected.isEmpty() && !selected.contains("\n")) {
            findBar.setQuery(selected);
        }
        findBar.setVisible(true);
        revalidate();
        repaint();
        findBar.focusQuery();
        findFromQueryChange();
    }

    public void hideFind() {
        findBar.setVisible(false);
        findBar.setStatus(" ");
        revalidate();
        repaint();
        CodeEditor editor = getActiveEditor();
        if (editor != null) {
            editor.getTextPane().requestFocusInWindow();
        }
    }

    public boolean isFindVisible() {
        return findBar.isVisible();
    }

    /** Package-private for tests. */
    FindBar findBar() {
        return findBar;
    }

    public boolean findNext() {
        return find(true);
    }

    public boolean findPrevious() {
        return find(false);
    }

    private void findFromQueryChange() {
        CodeEditor editor = getActiveEditor();
        if (editor == null) {
            findBar.setStatus("No file open");
            return;
        }
        String query = findBar.getQuery();
        if (query.isEmpty()) {
            findBar.setStatus(" ");
            return;
        }
        int from = editor.getSelectionStart();
        int index = TextFinder.findNext(
                editor.getDocumentText(), query, from, findBar.isMatchCase());
        applyFindResult(editor, query, index);
    }

    private boolean find(boolean forward) {
        CodeEditor editor = getActiveEditor();
        if (editor == null) {
            findBar.setStatus("No file open");
            return false;
        }
        if (!findBar.isVisible()) {
            showFind();
            return true;
        }
        String query = findBar.getQuery();
        if (query.isEmpty()) {
            findBar.setStatus("Enter search text");
            findBar.focusQuery();
            return false;
        }
        String text = editor.getDocumentText();
        int index;
        if (forward) {
            int from = editor.getSelectionEnd();
            if (editor.getSelectionStart() == editor.getSelectionEnd()) {
                from = editor.getCaretPosition();
            }
            index = TextFinder.findNext(text, query, from, findBar.isMatchCase());
        } else {
            int before = editor.getSelectionStart();
            if (editor.getSelectionStart() == editor.getSelectionEnd()) {
                before = editor.getCaretPosition();
            }
            index = TextFinder.findPrevious(text, query, before, findBar.isMatchCase());
        }
        return applyFindResult(editor, query, index);
    }

    private boolean applyFindResult(CodeEditor editor, String query, int index) {
        int total = TextFinder.countMatches(editor.getDocumentText(), query, findBar.isMatchCase());
        if (index < 0) {
            findBar.setStatus("No results");
            return false;
        }
        editor.selectRange(index, index + query.length());
        int occurrence = occurrenceNumber(editor.getDocumentText(), query, index, findBar.isMatchCase());
        findBar.setStatus(occurrence + " of " + total);
        return true;
    }

    static int occurrenceNumber(String text, String query, int matchIndex, boolean matchCase) {
        if (query == null || query.isEmpty() || matchIndex < 0) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while (from <= matchIndex) {
            int index = TextFinder.findNext(text, query, from, matchCase);
            // findNext wraps; ignore wrapped results past the scan window.
            if (index < 0 || index < from || index > matchIndex) {
                break;
            }
            count++;
            if (index == matchIndex) {
                return count;
            }
            from = index + Math.max(1, query.length());
        }
        return count;
    }

    public boolean saveActive() {
        CodeEditor editor = getActiveEditor();
        if (editor == null || editor.getFilePath() == null) {
            return false;
        }
        try {
            editor.save();
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not save file:\n" + ex.getMessage(),
                    "Save Failed",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    public boolean saveAll() {
        boolean ok = true;
        for (CodeEditor editor : openEditors.values()) {
            if (editor.isDirty() && editor.getFilePath() != null) {
                try {
                    editor.save();
                } catch (Exception ex) {
                    ok = false;
                    JOptionPane.showMessageDialog(
                            this,
                            "Could not save " + editor.getFilePath() + ":\n" + ex.getMessage(),
                            "Save Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        return ok;
    }

    public boolean hasDirtyEditors() {
        return openEditors.values().stream().anyMatch(CodeEditor::isDirty);
    }

    int getTabCount() {
        return tabs.getTabCount();
    }

    Component getTabHeaderAt(int index) {
        return tabs.getTabComponentAt(index);
    }

    int getSelectedTabIndex() {
        return tabs.getSelectedIndex();
    }

    void openUntitled(String title, String content, Language language) {
        CodeEditor editor = new CodeEditor();
        configureEditor(editor);
        editor.setPlainContent(content, language);
        editor.setDirtyListener(() -> {
            int index = tabs.indexOfComponent(editor);
            if (index >= 0) {
                Component tab = tabs.getTabComponentAt(index);
                if (tab instanceof TabHeader header) {
                    header.setTitle(title + (editor.isDirty() ? " *" : ""));
                } else {
                    tabs.setTitleAt(index, title + (editor.isDirty() ? " *" : ""));
                }
            }
            statusUpdater.run();
        });
        showTabs();
        tabs.addTab(title, editor);
        int index = tabs.indexOfComponent(editor);
        tabs.setTabComponentAt(index, new TabHeader(title, () -> closeTab(editor), this));
        tabs.setSelectedComponent(editor);
        statusUpdater.run();
    }

    private void maybeShowTabContextMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int index = tabs.indexAtLocation(e.getX(), e.getY());
        if (index < 0) {
            return;
        }
        tabs.setSelectedIndex(index);
        JPopupMenu menu = createTabContextMenu(index);
        menu.show(tabs, e.getX(), e.getY());
    }

    JPopupMenu createTabContextMenu(int tabIndex) {
        JPopupMenu menu = new JPopupMenu();
        Component component = tabs.getComponentAt(tabIndex);
        if (component instanceof CodeEditor editor) {
            JMenuItem close = new JMenuItem("Close");
            close.addActionListener(e -> closeTab(editor));
            menu.add(close);

            JMenuItem closeOthers = new JMenuItem("Close Others");
            closeOthers.setEnabled(tabs.getTabCount() > 1);
            closeOthers.addActionListener(e -> closeOtherTabs(component));
            menu.add(closeOthers);

            JMenuItem closeAll = new JMenuItem("Close All");
            closeAll.addActionListener(e -> closeAllTabs());
            menu.add(closeAll);
            addCopyPathItem(menu, editor.getFilePath());
            return menu;
        }
        if (component instanceof BinaryViewer viewer) {
            JMenuItem close = new JMenuItem("Close");
            close.addActionListener(e -> closeBinaryTab(viewer));
            menu.add(close);

            JMenuItem closeOthers = new JMenuItem("Close Others");
            closeOthers.setEnabled(tabs.getTabCount() > 1);
            closeOthers.addActionListener(e -> closeOtherTabs(component));
            menu.add(closeOthers);

            JMenuItem closeAll = new JMenuItem("Close All");
            closeAll.addActionListener(e -> closeAllTabs());
            menu.add(closeAll);
            addCopyPathItem(menu, viewer.getFilePath());
        }
        return menu;
    }

    private static void addCopyPathItem(JPopupMenu menu, Path path) {
        menu.addSeparator();
        JMenuItem copyPath = new JMenuItem("Copy Path");
        copyPath.setEnabled(path != null);
        copyPath.addActionListener(e -> PathClipboard.copy(path));
        menu.add(copyPath);
    }

    void retargetOpenFiles(Path from, Path to) {
        if (from == null || to == null) {
            return;
        }
        Map<Path, CodeEditor> nextEditors = new HashMap<>();
        for (Map.Entry<Path, CodeEditor> entry : openEditors.entrySet()) {
            Path remapped = FileRename.remapOpenPath(entry.getKey(), from, to);
            if (remapped != null) {
                entry.getValue().setFilePath(remapped);
                updateTabTitle(entry.getValue(), entry.getValue().getTitle());
                nextEditors.put(remapped.toAbsolutePath().normalize(), entry.getValue());
            } else {
                nextEditors.put(entry.getKey(), entry.getValue());
            }
        }
        openEditors.clear();
        openEditors.putAll(nextEditors);

        Map<Path, BinaryViewer> nextBinary = new HashMap<>();
        for (Map.Entry<Path, BinaryViewer> entry : openBinary.entrySet()) {
            Path remapped = FileRename.remapOpenPath(entry.getKey(), from, to);
            if (remapped != null) {
                entry.getValue().setFilePath(remapped);
                updateTabTitle(entry.getValue(), entry.getValue().getTitle());
                nextBinary.put(remapped.toAbsolutePath().normalize(), entry.getValue());
            } else {
                nextBinary.put(entry.getKey(), entry.getValue());
            }
        }
        openBinary.clear();
        openBinary.putAll(nextBinary);
        statusUpdater.run();
    }

    private void updateTabTitle(Component component, String title) {
        int index = tabs.indexOfComponent(component);
        if (index < 0) {
            return;
        }
        Component tab = tabs.getTabComponentAt(index);
        if (tab instanceof TabHeader header) {
            header.setTitle(title);
        } else {
            tabs.setTitleAt(index, title);
        }
    }

    void closeOtherTabs(Component keep) {
        List<Component> toClose = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component component = tabs.getComponentAt(i);
            if (component != keep) {
                toClose.add(component);
            }
        }
        for (Component component : toClose) {
            closeComponentTab(component);
        }
    }

    void closeOtherTabs(CodeEditor keep) {
        closeOtherTabs((Component) keep);
    }

    void closeAllTabs() {
        List<Component> toClose = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            toClose.add(tabs.getComponentAt(i));
        }
        for (Component component : toClose) {
            closeComponentTab(component);
        }
    }

    private void closeComponentTab(Component component) {
        if (component instanceof CodeEditor editor) {
            closeTab(editor);
        } else if (component instanceof BinaryViewer viewer) {
            closeBinaryTab(viewer);
        }
    }

    private void closeBinaryTab(BinaryViewer viewer) {
        if (viewer.getFilePath() != null) {
            openBinary.remove(viewer.getFilePath().toAbsolutePath().normalize());
        }
        tabs.remove(viewer);
        if (tabs.getTabCount() == 0) {
            showEmpty();
        }
        statusUpdater.run();
    }

    private void closeTab(CodeEditor editor) {
        if (editor.isDirty()) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Save changes to " + editor.getTitle().replace(" *", "") + "?",
                    "Unsaved Changes",
                    JOptionPane.YES_NO_CANCEL_OPTION);
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                return;
            }
            if (choice == JOptionPane.YES_OPTION) {
                try {
                    editor.save();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            this,
                            "Could not save file:\n" + ex.getMessage(),
                            "Save Failed",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
        }
        if (editor.getFilePath() != null) {
            openEditors.remove(editor.getFilePath().toAbsolutePath().normalize());
        }
        tabs.remove(editor);
        if (tabs.getTabCount() == 0) {
            showEmpty();
        }
        statusUpdater.run();
    }

    private void showEmpty() {
        contentHost.removeAll();
        contentHost.add(emptyLabel, BorderLayout.CENTER);
        contentHost.revalidate();
        contentHost.repaint();
    }

    private void showTabs() {
        if (contentHost.getComponentCount() == 1 && contentHost.getComponent(0) == tabs) {
            return;
        }
        contentHost.removeAll();
        contentHost.add(tabs, BorderLayout.CENTER);
        contentHost.revalidate();
        contentHost.repaint();
    }

    private static final class TabHeader extends JPanel {
        private final JLabel titleLabel;

        TabHeader(String title, Runnable onClose, EditorTabPane owner) {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            setOpaque(false);
            titleLabel = new JLabel(title);
            titleLabel.setFont(IdeTheme.UI_FONT.deriveFont(Font.PLAIN, 12f));
            titleLabel.setForeground(IdeTheme.FG);
            JButton close = new JButton(new TabCloseIcon(IdeTheme.FG_DIM));
            close.setToolTipText("Close");
            close.setMargin(new java.awt.Insets(0, 0, 0, 0));
            close.setPreferredSize(new Dimension(16, 16));
            close.setMaximumSize(new Dimension(16, 16));
            close.setFocusable(false);
            close.setBorderPainted(false);
            close.setContentAreaFilled(false);
            close.setOpaque(false);
            close.addActionListener(e -> onClose.run());
            add(titleLabel);
            add(close);

            MouseAdapter tabMouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    selectThisTab(owner);
                    showTabMenu(e, owner);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    showTabMenu(e, owner);
                }
            };
            addMouseListener(tabMouse);
            titleLabel.addMouseListener(tabMouse);
        }

        private void selectThisTab(EditorTabPane owner) {
            int index = owner.tabs.indexOfTabComponent(this);
            if (index >= 0) {
                owner.tabs.setSelectedIndex(index);
            }
        }

        private void showTabMenu(MouseEvent e, EditorTabPane owner) {
            if (!e.isPopupTrigger()) {
                return;
            }
            selectThisTab(owner);
            int index = owner.tabs.indexOfTabComponent(this);
            if (index < 0) {
                return;
            }
            JPopupMenu menu = owner.createTabContextMenu(index);
            menu.show(e.getComponent(), e.getX(), e.getY());
        }

        void setTitle(String title) {
            titleLabel.setText(title);
        }
    }

    /**
     * Painted X used for tab close buttons so we don't depend on font glyphs.
     */
    static final class TabCloseIcon implements Icon {
        private final java.awt.Color color;
        private final int size;

        TabCloseIcon(java.awt.Color color) {
            this(color, 10);
        }

        TabCloseIcon(java.awt.Color color, int size) {
            this.color = color;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int pad = 1;
                g2.drawLine(x + pad, y + pad, x + size - pad - 1, y + size - pad - 1);
                g2.drawLine(x + size - pad - 1, y + pad, x + pad, y + size - pad - 1);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
