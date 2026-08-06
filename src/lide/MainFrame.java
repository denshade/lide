package lide;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
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

/**
 * Main IDE window: project tree + tabbed syntax-highlighted editor.
 */
public final class MainFrame extends JFrame {
    private final ProjectTreePanel projectTree = new ProjectTreePanel();
    private final EditorTabPane editors = new EditorTabPane();
    private final JLabel statusLabel = new JLabel("Ready");

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

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, projectTree, editors);
        split.setDividerLocation(260);
        split.setResizeWeight(0.18);
        split.setBorder(null);
        split.setBackground(IdeTheme.BG);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(IdeTheme.BG_RAISED);
        statusBar.setBorder(new EmptyBorder(4, 10, 4, 10));
        statusLabel.setForeground(IdeTheme.FG_DIM);
        statusLabel.setFont(IdeTheme.UI_FONT.deriveFont(Font.PLAIN, 12f));
        statusBar.add(statusLabel, BorderLayout.WEST);

        setJMenuBar(buildMenuBar());
        add(split, BorderLayout.CENTER);
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
        file.add(openFile);
        file.addSeparator();
        file.add(save);
        file.add(saveAll);
        file.addSeparator();
        file.add(exit);

        JMenu view = new JMenu("View");
        view.setMnemonic(KeyEvent.VK_V);
        JMenuItem about = new JMenuItem("About Lide");
        about.addActionListener(e -> JOptionPane.showMessageDialog(
                this,
                "Lide — a lightweight Java IDE\n"
                        + "Open a project directory, browse files, and edit with syntax highlighting.",
                "About Lide",
                JOptionPane.INFORMATION_MESSAGE));
        view.add(about);

        bar.add(file);
        bar.add(view);
        return bar;
    }

    private void openDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        Path current = projectTree.getProjectRoot();
        if (current != null) {
            chooser.setCurrentDirectory(current.toFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path dir = chooser.getSelectedFile().toPath();
            projectTree.openDirectory(dir);
            setTitle("Lide — " + dir.getFileName());
            statusLabel.setText("Opened project: " + dir.toAbsolutePath());
        }
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
        if (editor == null || editor.getFilePath() == null) {
            Path root = projectTree.getProjectRoot();
            statusLabel.setText(root != null
                    ? "Project: " + root.toAbsolutePath()
                    : "Ready — open a directory to get started");
            return;
        }
        String dirty = editor.isDirty() ? " • modified" : "";
        Language lang = Language.fromPath(editor.getFilePath());
        statusLabel.setText(editor.getFilePath().toAbsolutePath()
                + "  |  " + lang.name() + dirty);
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
