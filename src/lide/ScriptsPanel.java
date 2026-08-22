package lide;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
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
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * Bottom panel listing project scripts and running them with captured output.
 */
public final class ScriptsPanel extends JPanel {
    static final int OUTPUT_FLUSH_MS = 50;
    static final int MAX_PENDING_CHARS = 64 * 1024;

    private final DefaultListModel<ScriptEntry> listModel = new DefaultListModel<>();
    private final JList<ScriptEntry> scriptList = new JList<>(listModel);
    private final JTextArea output = new JTextArea();
    private final JButton runButton = new JButton("Run");
    private final JButton stopButton = new JButton("Stop");
    private final JButton refreshButton = new JButton("Refresh");
    private final JLabel titleLabel = new JLabel("Scripts");
    private final AtomicReference<Process> running = new AtomicReference<>();
    private final AtomicReference<SwingWorker<Integer, Void>> worker = new AtomicReference<>();
    private final Object pendingLock = new Object();
    private final StringBuilder pendingOutput = new StringBuilder();
    private volatile boolean busy;
    private volatile boolean userStopped;
    private Timer outputFlushTimer;
    int maxConsoleChars = 256 * 1024;

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
        if (busy) {
            return true;
        }
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
        startProcess(ScriptCommand.processBuilder(script, projectRoot));
    }

    void runProcess(ProcessBuilder builder) {
        if (builder == null || builder.command().isEmpty() || isRunning()) {
            return;
        }
        startProcess(builder);
    }

    /**
     * Runs an arbitrary command in {@code workingDirectory} and streams output here.
     */
    void runCommand(List<String> command, Path workingDirectory) {
        if (command == null || command.isEmpty() || isRunning()) {
            return;
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        Path cwd = workingDirectory != null ? workingDirectory : projectRoot;
        if (cwd != null) {
            builder.directory(cwd.toAbsolutePath().normalize().toFile());
        }
        builder.redirectErrorStream(true);
        startProcess(builder);
    }

    private void startProcess(ProcessBuilder builder) {
        output.setText("");
        appendOutput("$ " + String.join(" ", builder.command()) + "\n");
        if (builder.directory() != null) {
            appendOutput("(cwd: " + builder.directory() + ")\n\n");
        }
        userStopped = false;
        synchronized (pendingLock) {
            pendingOutput.setLength(0);
        }
        busy = true;
        setRunningUi(true);
        startFlushTimer();

        SwingWorker<Integer, Void> next = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                Process process = builder.start();
                running.set(process);
                ProcessSupport.lowerPriority(process);
                try {
                    process.getOutputStream().close();
                } catch (IOException ignored) {
                }
                if (isCancelled() || userStopped) {
                    ProcessSupport.destroyTree(process);
                    return -1;
                }
                try (Reader reader = new InputStreamReader(
                        process.getInputStream(), Charset.defaultCharset())) {
                    char[] buf = new char[8192];
                    int n;
                    while ((n = reader.read(buf)) >= 0) {
                        if (isCancelled() || userStopped) {
                            break;
                        }
                        enqueueOutput(new String(buf, 0, n));
                    }
                } catch (IOException ex) {
                    if (!userStopped && !isCancelled()) {
                        enqueueOutput("\n[read error: " + ex.getMessage() + "]\n");
                    }
                }
                if (userStopped || isCancelled()) {
                    ProcessSupport.destroyTree(process);
                }
                return process.waitFor();
            }

            @Override
            protected void done() {
                running.set(null);
                worker.compareAndSet(this, null);
                busy = false;
                stopFlushTimerAndFlush();
                setRunningUi(false);
                if (userStopped || isCancelled()) {
                    appendOutput("\n[stopped]\n");
                    return;
                }
                try {
                    int code = get();
                    appendOutput("\n[exit code " + code + "]\n");
                } catch (CancellationException ex) {
                    appendOutput("\n[stopped]\n");
                } catch (Exception ex) {
                    appendOutput("\n[failed: " + ex.getMessage() + "]\n");
                }
            }
        };
        worker.set(next);
        next.execute();
    }

    void stopRunning() {
        userStopped = true;
        synchronized (pendingLock) {
            pendingLock.notifyAll();
        }
        ProcessSupport.destroyTree(running.get());
        SwingWorker<Integer, Void> current = worker.get();
        if (current != null) {
            current.cancel(true);
        }
    }

    private void setRunningUi(boolean runningNow) {
        runButton.setEnabled(!runningNow && scriptList.getSelectedValue() != null);
        stopButton.setEnabled(runningNow);
        refreshButton.setEnabled(!runningNow);
    }

    private void updateRunEnabled() {
        runButton.setEnabled(!isRunning() && scriptList.getSelectedValue() != null);
    }

    private void startFlushTimer() {
        if (outputFlushTimer == null) {
            outputFlushTimer = new Timer(OUTPUT_FLUSH_MS, e -> flushPendingOutput());
            outputFlushTimer.setRepeats(true);
        }
        outputFlushTimer.restart();
    }

    private void stopFlushTimerAndFlush() {
        if (outputFlushTimer != null) {
            outputFlushTimer.stop();
        }
        flushPendingOutput();
    }

    private void enqueueOutput(String chunk) {
        synchronized (pendingLock) {
            pendingOutput.append(chunk);
            while (pendingOutput.length() > MAX_PENDING_CHARS && !userStopped) {
                try {
                    pendingLock.wait(OUTPUT_FLUSH_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void flushPendingOutput() {
        String chunk;
        synchronized (pendingLock) {
            if (pendingOutput.length() == 0) {
                pendingLock.notifyAll();
                return;
            }
            chunk = pendingOutput.toString();
            pendingOutput.setLength(0);
            pendingLock.notifyAll();
        }
        appendOutput(normalizeNewlines(chunk));
    }

    static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private void appendOutput(String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> appendOutput(text));
            return;
        }
        output.append(text);
        Document doc = output.getDocument();
        int extra = doc.getLength() - maxConsoleChars;
        if (extra > 0) {
            try {
                doc.remove(0, extra);
            } catch (BadLocationException ignored) {
            }
        }
        output.setCaretPosition(doc.getLength());
    }

    record ScriptEntry(Path path, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
