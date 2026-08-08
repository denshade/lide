package lide;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

/**
 * Main IDE window: project tree + tabbed syntax-highlighted editor.
 */
public final class MainFrame extends JFrame {
    static final int ABOUT_ICON_SIZE = 64;

    private final ProjectTreePanel projectTree = new ProjectTreePanel();
    private final EditorTabPane editors = new EditorTabPane();
    private final JLabel statusLabel = new JLabel("Ready");
    private final ProjectHistory projectHistory = new ProjectHistory();
    private ScriptsPanel scriptsPanel;
    private JMenu openRecentMenu;

    public MainFrame() {
        super("Lide");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(960, 640));
        setSize(1280, 800);
        setLocationRelativeTo(null);
        getContentPane().setBackground(IdeTheme.BG);

        projectTree.setOpenFileHandler(editors::openFile);
        editors.setStatusUpdater(this::updateStatus);
        editors.setProjectRootSupplier(projectTree::getProjectRoot);

        JSplitPane editorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, projectTree, editors);
        editorSplit.setDividerLocation(260);
        editorSplit.setResizeWeight(0.18);
        editorSplit.setBorder(null);
        editorSplit.setBackground(IdeTheme.BG);

        ScriptsPanel scriptsPanel = new ScriptsPanel();
        this.scriptsPanel = scriptsPanel;

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorSplit, scriptsPanel);
        mainSplit.setResizeWeight(0.78);
        mainSplit.setDividerLocation(560);
        mainSplit.setBorder(null);
        mainSplit.setBackground(IdeTheme.BG);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(IdeTheme.BG_RAISED);
        statusBar.setBorder(new EmptyBorder(4, 10, 4, 10));
        statusLabel.setForeground(IdeTheme.FG_DIM);
        statusLabel.setFont(IdeTheme.UI_FONT.deriveFont(Font.PLAIN, 12f));
        statusBar.add(statusLabel, BorderLayout.WEST);

        setJMenuBar(buildMenuBar());
        setIconImages(AppIcons.loadWindowIcons());
        add(mainSplit, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitIde();
            }
        });
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.setBackground(IdeTheme.BG_RAISED);
        bar.setBorder(new EmptyBorder(2, 4, 2, 4));

        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);

        JMenuItem openDir = new JMenuItem("Open Directory…");
        openDir.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        openDir.addActionListener(e -> openDirectory());

        openRecentMenu = new JMenu("Open Recent");
        rebuildOpenRecentMenu();
        file.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                rebuildOpenRecentMenu();
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });

        JMenuItem openFile = new JMenuItem("Open File…");
        openFile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        openFile.addActionListener(e -> openFileDialog());

        JMenuItem save = new JMenuItem("Save");
        save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        save.addActionListener(e -> {
            if (editors.saveActive()) {
                updateStatus();
            }
        });

        JMenuItem saveAll = new JMenuItem("Save All");
        saveAll.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        saveAll.addActionListener(e -> {
            editors.saveAll();
            updateStatus();
        });

        JMenuItem exit = new JMenuItem("Exit");
        exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        exit.addActionListener(e -> exitIde());

        file.add(openDir);
        file.add(openRecentMenu);
        file.add(openFile);
        file.addSeparator();
        file.add(save);
        file.add(saveAll);
        file.addSeparator();
        file.add(exit);

        JMenu edit = buildEditMenu();

        JMenu view = new JMenu("View");
        view.setMnemonic(KeyEvent.VK_V);
        JMenuItem about = new JMenuItem("About Lide");
        about.addActionListener(e -> showAboutDialog());
        view.add(about);

        bar.add(file);
        bar.add(edit);
        bar.add(view);
        return bar;
    }

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(
                this,
                "Lide — a lightweight Java IDE\n"
                        + "Open a project directory, browse files, and edit with syntax highlighting.",
                "About Lide",
                JOptionPane.INFORMATION_MESSAGE,
                aboutDialogIcon());
    }

    static Icon aboutDialogIcon() {
        return AppIcons.dialogIcon(ABOUT_ICON_SIZE);
    }

    private void rebuildOpenRecentMenu() {
        openRecentMenu.removeAll();
        List<Path> recent = projectHistory.entries();
        if (recent.isEmpty()) {
            JMenuItem empty = new JMenuItem("(No recent projects)");
            empty.setEnabled(false);
            openRecentMenu.add(empty);
            return;
        }
        int index = 1;
        for (Path path : recent) {
            String label = path.getFileName() != null
                    ? path.getFileName() + "  —  " + path
                    : path.toString();
            JMenuItem item = new JMenuItem(index + "  " + label);
            Path target = path;
            item.addActionListener(e -> openProjectDirectory(target));
            openRecentMenu.add(item);
            index++;
        }
        openRecentMenu.addSeparator();
        JMenuItem clear = new JMenuItem("Clear Recent Projects");
        clear.addActionListener(e -> {
            projectHistory.clear();
            rebuildOpenRecentMenu();
        });
        openRecentMenu.add(clear);
    }

    private JMenu buildEditMenu() {
        JMenu edit = new JMenu("Edit");
        edit.setMnemonic(KeyEvent.VK_E);

        JMenuItem undo = new JMenuItem("Undo");
        undo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        undo.addActionListener(e -> editors.undoActive());

        JMenuItem redo = new JMenuItem("Redo");
        redo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        redo.addActionListener(e -> editors.redoActive());

        JMenuItem copy = new JMenuItem("Copy");
        copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        copy.addActionListener(e -> editors.copyActive());

        JMenuItem paste = new JMenuItem("Paste");
        paste.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));
        paste.addActionListener(e -> editors.pasteActive());

        JMenuItem find = new JMenuItem("Find…");
        find.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
        find.addActionListener(e -> editors.showFind());

        JMenuItem findNext = new JMenuItem("Find Next");
        findNext.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
        findNext.addActionListener(e -> editors.findNext());

        JMenuItem findPrevious = new JMenuItem("Find Previous");
        findPrevious.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK));
        findPrevious.addActionListener(e -> editors.findPrevious());

        edit.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                boolean hasEditor = editors.hasActiveEditor();
                undo.setEnabled(editors.canUndoActive());
                redo.setEnabled(editors.canRedoActive());
                copy.setEnabled(hasEditor);
                paste.setEnabled(hasEditor);
                find.setEnabled(hasEditor);
                findNext.setEnabled(hasEditor);
                findPrevious.setEnabled(hasEditor);
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });

        edit.add(undo);
        edit.add(redo);
        edit.addSeparator();
        edit.add(copy);
        edit.add(paste);
        edit.addSeparator();
        edit.add(find);
        edit.add(findNext);
        edit.add(findPrevious);
        return edit;
    }

    private void openDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        Path current = projectTree.getProjectRoot();
        if (current != null) {
            chooser.setCurrentDirectory(current.toFile());
        } else {
            List<Path> recent = projectHistory.entries();
            if (!recent.isEmpty()) {
                chooser.setCurrentDirectory(recent.get(0).toFile());
            }
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openProjectDirectory(chooser.getSelectedFile().toPath());
        }
    }

    void openProjectDirectory(Path directory) {
        Path dir = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            projectHistory.remove(dir);
            rebuildOpenRecentMenu();
            JOptionPane.showMessageDialog(
                    this,
                    "Directory no longer exists:\n" + dir,
                    "Open Recent Failed",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        projectTree.openDirectory(dir);
        projectHistory.remember(dir);
        scriptsPanel.setProjectRoot(dir);
        rebuildOpenRecentMenu();
        setTitle("Lide — " + (dir.getFileName() != null ? dir.getFileName() : dir));
        statusLabel.setText("Opened project: " + dir);
    }

    private void openFileDialog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open File");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        Path current = projectTree.getProjectRoot();
        if (current != null) {
            chooser.setCurrentDirectory(current.toFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            editors.openFile(chooser.getSelectedFile().toPath());
            updateStatus();
        }
    }

    private void updateStatus() {
        CodeEditor editor = editors.getActiveEditor();
        if (editor != null && editor.getFilePath() != null) {
            String dirty = editor.isDirty() ? " • modified" : "";
            Language lang = Language.fromPath(editor.getFilePath());
            statusLabel.setText(editor.getFilePath().toAbsolutePath()
                    + "  |  " + lang.name() + dirty);
            return;
        }
        BinaryViewer binary = editors.getActiveBinaryViewer();
        if (binary != null && binary.getFilePath() != null) {
            statusLabel.setText(binary.getFilePath().toAbsolutePath()
                    + "  |  binary  |  " + binary.getFileSize() + " bytes");
            return;
        }
        Path root = projectTree.getProjectRoot();
        statusLabel.setText(root != null
                ? "Project: " + root.toAbsolutePath()
                : "Ready — open a directory to get started");
    }

    private void exitIde() {
        if (editors.hasDirtyEditors()) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "You have unsaved changes. Save all before exiting?",
                    "Unsaved Changes",
                    JOptionPane.YES_NO_CANCEL_OPTION);
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                return;
            }
            if (choice == JOptionPane.YES_OPTION) {
                editors.saveAll();
            }
        }
        dispose();
        System.exit(0);
    }
}
