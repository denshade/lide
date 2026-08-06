package lide;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final JLabel emptyLabel;
    private Runnable statusUpdater = () -> {
    };
    private java.util.function.Supplier<Path> projectRootSupplier = () -> null;

    public EditorTabPane() {
        super(new BorderLayout());
        setBackground(IdeTheme.BG);

        emptyLabel = new JLabel(
                "<html><div style='text-align:center;color:#808080'>"
                        + "<div style='font-size:18px;margin-bottom:8px'>Lide</div>"
                        + "<div>Open a directory (File → Open Directory) and double-click a file.</div>"
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

    public void openFile(Path path, int caretOffset) {
        Path normalized = path.toAbsolutePath().normalize();
        CodeEditor existing = openEditors.get(normalized);
        if (existing != null) {
            tabs.setSelectedComponent(existing);
            showTabs();
            if (caretOffset >= 0) {
                existing.goToOffset(caretOffset);
            }
            statusUpdater.run();
            return;
        }

        CodeEditor editor = new CodeEditor();
        configureEditor(editor);
        try {
            editor.openFile(normalized);
        } catch (Exception ex) {
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
        if (!(component instanceof CodeEditor editor)) {
            return menu;
        }

        JMenuItem close = new JMenuItem("Close");
        close.addActionListener(e -> closeTab(editor));
        menu.add(close);

        JMenuItem closeOthers = new JMenuItem("Close Others");
        closeOthers.setEnabled(tabs.getTabCount() > 1);
        closeOthers.addActionListener(e -> closeOtherTabs(editor));
        menu.add(closeOthers);

        JMenuItem closeAll = new JMenuItem("Close All");
        closeAll.addActionListener(e -> closeAllTabs());
        menu.add(closeAll);

        return menu;
    }

    void closeOtherTabs(CodeEditor keep) {
        List<CodeEditor> toClose = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component component = tabs.getComponentAt(i);
            if (component instanceof CodeEditor editor && editor != keep) {
                toClose.add(editor);
            }
        }
        for (CodeEditor editor : toClose) {
            closeTab(editor);
        }
    }

    void closeAllTabs() {
        List<CodeEditor> toClose = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component component = tabs.getComponentAt(i);
            if (component instanceof CodeEditor editor) {
                toClose.add(editor);
            }
        }
        for (CodeEditor editor : toClose) {
            closeTab(editor);
        }
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
        removeAll();
        add(emptyLabel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void showTabs() {
        if (getComponentCount() == 1 && getComponent(0) == tabs) {
            return;
        }
        removeAll();
        add(tabs, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private static final class TabHeader extends JPanel {
        private final JLabel titleLabel;

        TabHeader(String title, Runnable onClose, EditorTabPane owner) {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            setOpaque(false);
            titleLabel = new JLabel(title);
            titleLabel.setFont(IdeTheme.UI_FONT.deriveFont(Font.PLAIN, 12f));
            titleLabel.setForeground(IdeTheme.FG);
            JButton close = new JButton("×");
            close.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 12f));
            close.setMargin(new java.awt.Insets(0, 4, 0, 4));
            close.setPreferredSize(new Dimension(18, 18));
            close.setFocusable(false);
            close.setBorderPainted(false);
            close.setContentAreaFilled(false);
            close.setForeground(IdeTheme.FG_DIM);
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
}
