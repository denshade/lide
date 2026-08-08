package lide;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

/**
 * Bottom panel listing project scripts and running them with captured output.
 */
public final class ScriptsPanel extends JPanel {
    private final DefaultListModel<ScriptEntry> listModel = new DefaultListModel<>();
    private final JList<ScriptEntry> scriptList = new JList<>(listModel);
    private final JTextArea output = new JTextArea();
    private final JButton runButton = new JButton("Run");
    private final JButton stopButton = new JButton("Stop");
    private final JButton refreshButton = new JButton("Refresh");
    private final JLabel titleLabel = new JLabel("Scripts");
    private final AtomicReference<Process> running = new AtomicReference<>();

    private Path projectRoot;

    public ScriptsPanel() {
        super(new BorderLayout());
        setBackground(IdeTheme.BG);
        setPreferredSize(new Dimension(200, 180));
        setMinimumSize(new Dimension(120, 100));

        titleLabel.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 12f));
        titleLabel.setForeground(IdeTheme.FG);
        styleButton(refreshButton);
        styleButton(runButton);
        styleButton(stopButton);
        stopButton.setEnabled(false);

        refreshButton.addActionListener(e -> refresh());
        runButton.addActionListener(e -> runSelected());
        stopButton.addActionListener(e -> stopRunning());

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(IdeTheme.BG_RAISED);
        toolbar.setBorder(new EmptyBorder(4, 8, 4, 8));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(titleLabel);
        left.add(refreshButton);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(runButton);
        right.add(stopButton);
        toolbar.add(left, BorderLayout.WEST);
        toolbar.add(right, BorderLayout.EAST);

        scriptList.setFont(IdeTheme.UI_FONT);
        scriptList.setBackground(IdeTheme.BG_TREE);
        scriptList.setForeground(IdeTheme.FG);
        scriptList.setSelectionBackground(IdeTheme.SELECTION);
        scriptList.setSelectionForeground(java.awt.Color.WHITE);
        scriptList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        scriptList.addListSelectionListener(e -> updateRunEnabled());
        scriptList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    runSelected();
                }
            }
        });

        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        output.setBackground(IdeTheme.BG_EDITOR);
        output.setForeground(IdeTheme.DEFAULT_TEXT);
        output.setCaretColor(IdeTheme.CARET);
        output.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JScrollPane listScroll = new JScrollPane(scriptList);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listScroll.getViewport().setBackground(IdeTheme.BG_TREE);
        listScroll.setPreferredSize(new Dimension(260, 120));

        JScrollPane outputScroll = new JScrollPane(output);
        outputScroll.setBorder(BorderFactory.createEmptyBorder());
        outputScroll.getViewport().setBackground(IdeTheme.BG_EDITOR);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, outputScroll);
        split.setResizeWeight(0.28);
        split.setBorder(null);
        split.setDividerSize(4);
        split.setBackground(IdeTheme.BG);

        add(toolbar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        setProjectRoot(null);
    }

    private static void styleButton(JButton button) {
        button.setFont(IdeTheme.UI_FONT);
        button.setFocusable(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(IdeTheme.BORDER),
                BorderFactory.createEmptyBorder(2, 10, 2, 10)));
        button.addPropertyChangeListener("enabled", e -> applyButtonColors(button));
        applyButtonColors(button);
    }

    static void applyButtonColors(JButton button) {
        if (button.isEnabled()) {
            button.setForeground(IdeTheme.FG);
            button.setBackground(IdeTheme.BG_RAISED);
        } else {
            button.setForeground(IdeTheme.FG_DISABLED);
            button.setBackground(IdeTheme.BG);
        }
    }

    public void setProjectRoot(Path projectRoot) {
        this.projectRoot = projectRoot == null ? null : projectRoot.toAbsolutePath().normalize();
        refresh();
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public void refresh() {
        listModel.clear();
        if (projectRoot == null) {
            titleLabel.setText("Scripts — open a project");
            updateRunEnabled();
            return;
        }
        List<Path> scripts = ScriptFinder.findScripts(projectRoot);
        for (Path script : scripts) {
            listModel.addElement(new ScriptEntry(script, ScriptFinder.displayName(projectRoot, script)));
        }
        titleLabel.setText("Scripts (" + scripts.size() + ")");
        if (!listModel.isEmpty() && scriptList.getSelectedIndex() < 0) {
            scriptList.setSelectedIndex(0);
        }
        updateRunEnabled();
    }

    public int getScriptCount() {
        return listModel.size();
    }

    public String getOutputText() {
        return output.getText();
    }

    public boolean isRunning() {
        Process process = running.get();
        return process != null && process.isAlive();
    }

    void runSelected() {
        ScriptEntry selected = scriptList.getSelectedValue();
        if (selected == null || isRunning() || projectRoot == null) {
            return;
        }
        runScript(selected.path());
    }

    void runScript(Path script) {
        if (script == null || projectRoot == null || isRunning()) {
            return;
        }
        ProcessBuilder builder = ScriptCommand.processBuilder(script, projectRoot);
        output.setText("");
        appendOutput("$ " + String.join(" ", builder.command()) + "\n");
        appendOutput("(cwd: " + projectRoot + ")\n\n");
        setRunningUi(true);

        SwingWorker<Integer, String> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                Process process = builder.start();
                running.set(process);
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        publish(line);
                    }
                }
                return process.waitFor();
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    appendOutput(line + "\n");
                }
            }

            @Override
            protected void done() {
                running.set(null);
                setRunningUi(false);
                try {
                    int code = get();
                    appendOutput("\n[exit code " + code + "]\n");
                } catch (Exception ex) {
                    appendOutput("\n[failed: " + ex.getMessage() + "]\n");
                }
            }
        };
        worker.execute();
    }

    void stopRunning() {
        Process process = running.getAndSet(null);
        if (process != null) {
            process.destroyForcibly();
            appendOutput("\n[stopped]\n");
        }
        setRunningUi(false);
    }

    private void setRunningUi(boolean runningNow) {
        runButton.setEnabled(!runningNow && scriptList.getSelectedValue() != null);
        stopButton.setEnabled(runningNow);
        refreshButton.setEnabled(!runningNow);
    }

    private void updateRunEnabled() {
        runButton.setEnabled(!isRunning() && scriptList.getSelectedValue() != null);
    }

    private void appendOutput(String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> appendOutput(text));
            return;
        }
        output.append(text);
        output.setCaretPosition(output.getDocument().getLength());
    }

    record ScriptEntry(Path path, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
