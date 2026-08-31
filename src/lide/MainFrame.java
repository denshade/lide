package lide;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    private FindInFilesPanel findInFilesPanel;
    private JMenu openRecentMenu;
    private JMenuItem backItem;
    private JMenuItem forwardItem;
    private JMenuItem toTestItem;
    private JMenuItem toImplementationItem;
    private final List<JMenuItem> ladleItems = new ArrayList<>();
    private final KeyEventDispatcher ladleHotkeyDispatcher =
            e -> LadleHotkeys.dispatch(e, this, this::runLadle);
    private final KeyEventDispatcher navigationHotkeyDispatcher =
            e -> NavigationHotkeys.dispatch(e, this, this::handleNavigationHotkey);

    public MainFrame() {
        super("Lide");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(960, 640));
        setSize(1280, 800);
        setLocationRelativeTo(null);
        getContentPane().setBackground(IdeTheme.BG);

        projectTree.setOpenFileHandler(editors::openFile);
        projectTree.setNewFileHandler(this::promptNewFile);
        projectTree.setRenameHandler(this::promptRename);
        editors.setStatusUpdater(this::updateStatus);
        editors.setNavigationListener(this::updateNavigateMenu);
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

        FindInFilesPanel findInFilesPanel = new FindInFilesPanel();
        this.findInFilesPanel = findInFilesPanel;
        findInFilesPanel.setOpenMatchHandler(match -> {
            editors.openMatch(match.file(), match.offset(), match.length());
            updateStatus();
        });

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(IdeTheme.BG);
        body.add(mainSplit, BorderLayout.CENTER);
        body.add(findInFilesPanel, BorderLayout.SOUTH);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(IdeTheme.BG_RAISED);
        statusBar.setBorder(new EmptyBorder(4, 10, 4, 10));
        statusLabel.setForeground(IdeTheme.FG_DIM);
        statusLabel.setFont(IdeTheme.UI_FONT.deriveFont(Font.PLAIN, 12f));
        statusBar.add(statusLabel, BorderLayout.WEST);

        setJMenuBar(buildMenuBar());
        setIconImages(AppIcons.loadWindowIcons());
        add(body, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitIde();
            }
        });
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(ladleHotkeyDispatcher);
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(navigationHotkeyDispatcher);
    }

    @Override
    public void dispose() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(ladleHotkeyDispatcher);
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(navigationHotkeyDispatcher);
        super.dispose();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.setBackground(IdeTheme.BG_RAISED);
        bar.setBorder(new EmptyBorder(2, 4, 2, 4));

        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);

        JMenuItem newFile = new JMenuItem("New File…");
        newFile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
        newFile.addActionListener(e -> promptNewFile(projectTree.getSelectedDirectory()));

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

        file.add(newFile);
        file.addSeparator();
        file.add(openDir);
        file.add(openRecentMenu);
        file.add(openFile);
        file.addSeparator();
        file.add(save);
        file.add(saveAll);
        file.addSeparator();
        file.add(exit);

        JMenu edit = buildEditMenu();

        JMenu navigate = buildNavigateMenu();

        JMenu ladle = buildLadleMenu();

        JMenu view = new JMenu("View");
        view.setMnemonic(KeyEvent.VK_V);
        JMenuItem about = new JMenuItem("About Lide");
        about.addActionListener(e -> showAboutDialog());
        view.add(about);

        bar.add(file);
        bar.add(edit);
        bar.add(navigate);
        bar.add(ladle);
        bar.add(view);
        return bar;
    }

    private JMenu buildNavigateMenu() {
        JMenu navigate = new JMenu("Navigate");
        navigate.setMnemonic(KeyEvent.VK_N);

        backItem = new JMenuItem("Back");
        backItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK));
        backItem.addActionListener(e -> {
            editors.navigateBack();
            updateStatus();
            updateNavigateMenu();
        });

        forwardItem = new JMenuItem("Forward");
        forwardItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK));
        forwardItem.addActionListener(e -> {
            editors.navigateForward();
            updateStatus();
            updateNavigateMenu();
        });

        navigate.add(backItem);
        navigate.add(forwardItem);
        navigate.addSeparator();

        toTestItem = new JMenuItem("To Test");
        toTestItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        toTestItem.addActionListener(e -> {
            editors.navigateToTest();
            updateStatus();
            updateNavigateMenu();
        });

        toImplementationItem = new JMenuItem("To Implementation");
        toImplementationItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        toImplementationItem.addActionListener(e -> {
            editors.navigateToImplementation();
            updateStatus();
            updateNavigateMenu();
        });

        navigate.add(toTestItem);
        navigate.add(toImplementationItem);
        updateNavigateMenu();

        navigate.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                updateNavigateMenu();
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        return navigate;
    }

    void updateNavigateMenu() {
        if (backItem == null || forwardItem == null) {
            return;
        }
        backItem.setEnabled(editors.canNavigateBack());
        forwardItem.setEnabled(editors.canNavigateForward());
        if (toTestItem != null) {
            toTestItem.setEnabled(editors.canNavigateToTest());
        }
        if (toImplementationItem != null) {
            toImplementationItem.setEnabled(editors.canNavigateToImplementation());
        }
    }

    void handleNavigationHotkey(NavigationHotkeys.Action action) {
        switch (action) {
            case FORWARD -> editors.navigateForward();
            case TO_TEST -> editors.navigateToTest();
            case TO_IMPLEMENTATION -> editors.navigateToImplementation();
            default -> editors.navigateBack();
        }
        updateStatus();
        updateNavigateMenu();
    }

    private JMenu buildLadleMenu() {
        JMenu ladle = new JMenu("Ladle");
        ladle.setMnemonic(KeyEvent.VK_L);

        JMenuItem install = new JMenuItem("Install Ladle");
        install.addActionListener(e -> installLadle());

        JMenuItem build = ladleItem("Build", LadleCommand.BUILD);
        build.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        JMenuItem test = ladleItem("Test", LadleCommand.TEST);
        test.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0));
        JMenuItem release = ladleItem("Release", LadleCommand.RELEASE);
        JMenuItem dependencies = ladleItem("Download Dependencies", LadleCommand.DEPENDENCY);
        dependencies.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0));
        JMenuItem clear = ladleItem("Clear", LadleCommand.CLEAR);

        ladle.add(install);
        ladle.addSeparator();
        ladle.add(build);
        ladle.add(test);
        ladle.addSeparator();
        ladle.add(release);
        ladle.add(dependencies);
        ladle.add(clear);

        ladleItems.add(install);
        ladleItems.add(build);
        ladleItems.add(test);
        ladleItems.add(release);
        ladleItems.add(dependencies);
        ladleItems.add(clear);
        updateLadleMenu();

        ladle.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                updateLadleMenu();
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        return ladle;
    }

    private JMenuItem ladleItem(String label, String command) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> runLadle(command));
        return item;
    }

    void updateLadleMenu() {
        boolean enable = projectTree.getProjectRoot() != null && !scriptsPanel.isRunning();
        for (JMenuItem item : ladleItems) {
            item.setEnabled(enable);
        }
    }

    void runLadle(String command) {
        Path root = projectTree.getProjectRoot();
        if (root == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Open a project directory first.",
                    "Ladle",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!LadleCommand.isAvailable(root)) {
            JOptionPane.showMessageDialog(
                    this,
                    "This project is not a Ladle project.\n"
                            + "Use Ladle → Install Ladle, or add lib/ladle.jar and build.ini.",
                    "Ladle",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (scriptsPanel.isRunning()) {
            return;
        }
        editors.saveAll();
        scriptsPanel.runProcess(LadleCommand.processBuilder(root, command));
        updateLadleMenu();
    }

    void installLadle() {
        Path root = projectTree.getProjectRoot();
        if (root == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Open a project directory first.",
                    "Install Ladle",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Path source = LadleInstaller.findDistribution(root);
        if (source == null) {
            source = chooseLadleDistribution(root);
            if (source == null) {
                return;
            }
        }
        try {
            LadleInstaller.Result result = LadleInstaller.install(source, root);
            projectTree.openDirectory(root);
            scriptsPanel.refresh();
            StringBuilder message = new StringBuilder();
            message.append("Installed Ladle from:\n").append(result.source()).append("\n\n");
            message.append("Copied ").append(result.jar());
            if (!result.scripts().isEmpty()) {
                message.append("\nand ").append(result.scripts().size()).append(" launcher script(s)");
            }
            if (result.wroteIni()) {
                message.append("\n\nWrote a starter build.ini. Edit it to match this project.");
            }
            JOptionPane.showMessageDialog(
                    this,
                    message.toString(),
                    "Install Ladle",
                    JOptionPane.INFORMATION_MESSAGE);
            statusLabel.setText("Installed Ladle in " + root);
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not install Ladle:\n" + ex.getMessage(),
                    "Install Ladle",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private Path chooseLadleDistribution(Path projectRoot) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select a Ladle distribution (folder with lib/ladle.jar)");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        Path start = projectRoot.getParent() != null ? projectRoot.getParent() : projectRoot;
        chooser.setCurrentDirectory(start.toFile());
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        Path chosen = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (!LadleInstaller.isDistribution(chosen)) {
            JOptionPane.showMessageDialog(
                    this,
                    "That folder is not a Ladle distribution.\nIt must contain lib/ladle.jar.",
                    "Install Ladle",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        if (chosen.equals(projectRoot.toAbsolutePath().normalize())) {
            JOptionPane.showMessageDialog(
                    this,
                    "Choose a different folder than the open project.",
                    "Install Ladle",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return chosen;
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

        JMenuItem findInFiles = new JMenuItem("Find in Files…");
        findInFiles.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        findInFiles.addActionListener(e -> showFindInFiles());

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
        edit.add(findInFiles);
        edit.add(findNext);
        edit.add(findPrevious);
        return edit;
    }

    void showFindInFiles() {
        findInFilesPanel.setMinimized(false);
        CodeEditor editor = editors.getActiveEditor();
        if (editor != null) {
            String selected = editor.getSelectedText();
            if (selected != null && !selected.isEmpty() && !selected.contains("\n")) {
                findInFilesPanel.setQuery(selected);
                findInFilesPanel.runSearch();
                return;
            }
        }
        findInFilesPanel.focusQuery();
    }

    FindInFilesPanel findInFilesPanel() {
        return findInFilesPanel;
    }

    void promptNewFile(Path directory) {
        if (directory == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Open a project directory first.",
                    "New File",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = JOptionPane.showInputDialog(
                this,
                "File name:",
                "New File",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }
        createNewFileIn(directory, name);
    }

    Path createNewFileIn(Path directory, String name) {
        try {
            Path created = NewFile.create(directory, name);
            projectTree.refreshDirectory(directory);
            scriptsPanel.refresh();
            editors.openFile(created);
            CodeEditor editor = editors.getActiveEditor();
            if (editor != null) {
                editor.getTextPane().requestFocusInWindow();
            }
            updateStatus();
            return created;
        } catch (java.nio.file.FileAlreadyExistsException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "A file with that name already exists.",
                    "New File",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        } catch (IllegalArgumentException | IOException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not create file:\n" + ex.getMessage(),
                    "New File",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    void promptRename(Path path) {
        if (path == null) {
            return;
        }
        if (projectTree.isProjectRoot(path)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Cannot rename the open project folder.",
                    "Rename",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String current = path.getFileName() != null ? path.getFileName().toString() : path.toString();
        Object entered = JOptionPane.showInputDialog(
                this,
                "New name:",
                "Rename",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                current);
        if (entered == null) {
            return;
        }
        renamePath(path, entered.toString());
    }

    Path renamePath(Path source, String newName) {
        if (projectTree.isProjectRoot(source)) {
            return null;
        }
        try {
            Path from = source.toAbsolutePath().normalize();
            Path renamed = FileRename.rename(from, newName);
            editors.retargetOpenFiles(from, renamed);
            Path parent = renamed.getParent();
            if (parent != null) {
                projectTree.refreshDirectory(parent);
            }
            scriptsPanel.refresh();
            updateStatus();
            return renamed;
        } catch (java.nio.file.FileAlreadyExistsException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "A file or folder with that name already exists.",
                    "Rename",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        } catch (IllegalArgumentException | IOException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not rename:\n" + ex.getMessage(),
                    "Rename",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    EditorTabPane editorTabs() {
        return editors;
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
        projectHistory.remember(dir);
        applyProjectDirectory(dir);
        rebuildOpenRecentMenu();
    }

    void applyProjectDirectory(Path dir) {
        projectTree.openDirectory(dir);
        scriptsPanel.setProjectRoot(dir);
        findInFilesPanel.setProjectRoot(dir);
        updateLadleMenu();
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
        scriptsPanel.stopRunning();
        dispose();
        System.exit(0);
    }
}
