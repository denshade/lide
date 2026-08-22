package lide;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

/**
 * Bottom panel for searching the open project and listing matches.
 */
public final class FindInFilesPanel extends JPanel {
    static final int TITLE_HEIGHT = 30;
    static final int EXPANDED_HEIGHT = 200;

    private final JLabel titleLabel = new JLabel("Find in Files");
    private final JButton minimizeButton = new JButton("−");
    private final JTextField queryField = new JTextField(28);
    private final JCheckBox matchCase = new JCheckBox("Match case");
    private final JButton findButton = new JButton("Find");
    private final JLabel statusLabel = new JLabel(" ");
    private final DefaultListModel<ProjectFinder.Match> listModel = new DefaultListModel<>();
    private final JList<ProjectFinder.Match> resultList = new JList<>(listModel);
    private final JPanel body = new JPanel(new BorderLayout());
    private final JPanel toolbar = new JPanel(new BorderLayout());

    private Path projectRoot;
    private boolean minimized = true;
    private Consumer<ProjectFinder.Match> openMatchHandler = match -> {
    };
    private SwingWorker<ProjectFinder.Result, Void> worker;

    public FindInFilesPanel() {
        super(new BorderLayout());
        setBackground(IdeTheme.BG);
        applySizes();

        titleLabel.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 12f));
        titleLabel.setForeground(IdeTheme.FG);
        titleLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (minimized) {
                    setMinimized(false);
                    focusQuery();
                }
            }
        });

        styleButton(minimizeButton);
        minimizeButton.setPreferredSize(new Dimension(28, 22));
        minimizeButton.addActionListener(e -> setMinimized(!minimized));

        toolbar.setBackground(IdeTheme.BG_RAISED);
        toolbar.setBorder(new EmptyBorder(4, 8, 4, 8));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(titleLabel);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(minimizeButton);
        toolbar.add(left, BorderLayout.WEST);
        toolbar.add(right, BorderLayout.EAST);

        queryField.setFont(IdeTheme.UI_FONT);
        queryField.setBackground(IdeTheme.BG_EDITOR);
        queryField.setForeground(IdeTheme.FG);
        queryField.setCaretColor(IdeTheme.CARET);
        queryField.setSelectionColor(IdeTheme.SELECTION);
        queryField.setSelectedTextColor(Color.WHITE);
        queryField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(IdeTheme.BORDER),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        queryField.addActionListener(e -> runSearch());

        matchCase.setForeground(IdeTheme.FG);
        matchCase.setBackground(IdeTheme.BG);
        matchCase.setFont(IdeTheme.UI_FONT);
        matchCase.setFocusable(false);
        matchCase.addActionListener(e -> {
            if (!queryField.getText().isEmpty()) {
                runSearch();
            }
        });

        styleButton(findButton);
        findButton.addActionListener(e -> runSearch());

        statusLabel.setForeground(IdeTheme.FG_DIM);
        statusLabel.setFont(IdeTheme.UI_FONT.deriveFont(12f));

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        searchRow.setBackground(IdeTheme.BG);
        JLabel findLabel = new JLabel("Find:");
        findLabel.setForeground(IdeTheme.FG);
        findLabel.setFont(IdeTheme.UI_FONT);
        searchRow.add(findLabel);
        searchRow.add(queryField);
        searchRow.add(findButton);
        searchRow.add(matchCase);
        searchRow.add(statusLabel);

        resultList.setFont(IdeTheme.UI_FONT);
        resultList.setBackground(IdeTheme.BG_TREE);
        resultList.setForeground(IdeTheme.FG);
        resultList.setSelectionBackground(IdeTheme.SELECTION);
        resultList.setSelectionForeground(Color.WHITE);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelected();
                }
            }
        });
        resultList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    openSelected();
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(resultList);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listScroll.getViewport().setBackground(IdeTheme.BG_TREE);

        body.setBackground(IdeTheme.BG);
        body.add(searchRow, BorderLayout.NORTH);
        body.add(listScroll, BorderLayout.CENTER);

        add(toolbar, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        setMinimized(true);
    }

    private static void styleButton(JButton button) {
        button.setFont(IdeTheme.UI_FONT);
        button.setFocusable(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(IdeTheme.BORDER),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        button.addPropertyChangeListener("enabled", e -> ScriptsPanel.applyButtonColors(button));
        ScriptsPanel.applyButtonColors(button);
    }

    public void setOpenMatchHandler(Consumer<ProjectFinder.Match> openMatchHandler) {
        this.openMatchHandler = openMatchHandler == null ? match -> {
        } : openMatchHandler;
    }

    public void setProjectRoot(Path projectRoot) {
        this.projectRoot = projectRoot == null ? null : projectRoot.toAbsolutePath().normalize();
        if (this.projectRoot == null) {
            titleLabel.setText("Find in Files");
        }
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public boolean isMinimized() {
        return minimized;
    }

    public void setMinimized(boolean minimized) {
        this.minimized = minimized;
        body.setVisible(!minimized);
        minimizeButton.setText(minimized ? "+" : "−");
        minimizeButton.setToolTipText(minimized ? "Restore" : "Minimize");
        applySizes();
        revalidate();
        repaint();
        if (getParent() != null) {
            getParent().revalidate();
            getParent().repaint();
        }
    }

    public void focusQuery() {
        setMinimized(false);
        queryField.requestFocusInWindow();
        queryField.selectAll();
    }

    public String getQuery() {
        return queryField.getText();
    }

    public void setQuery(String query) {
        queryField.setText(query == null ? "" : query);
        queryField.selectAll();
    }

    public boolean isMatchCase() {
        return matchCase.isSelected();
    }

    public void setMatchCase(boolean matchCase) {
        this.matchCase.setSelected(matchCase);
    }

    public String getStatus() {
        return statusLabel.getText();
    }

    public int getResultCount() {
        return listModel.size();
    }

    ProjectFinder.Match getResult(int index) {
        return listModel.get(index);
    }

    boolean isBodyVisible() {
        return body.isVisible();
    }

    public void runSearch() {
        setMinimized(false);
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        String query = queryField.getText();
        if (projectRoot == null) {
            applyResult(null, "Open a project directory first");
            return;
        }
        if (query == null || query.isEmpty()) {
            applyResult(null, "Enter search text");
            return;
        }
        statusLabel.setText("Searching…");
        Path root = projectRoot;
        boolean caseSensitive = matchCase.isSelected();
        worker = new SwingWorker<>() {
            @Override
            protected ProjectFinder.Result doInBackground() {
                return ProjectFinder.search(root, query, caseSensitive, ProjectFinder.DEFAULT_MAX_MATCHES,
                        ProjectFinder.DEFAULT_MAX_FILES, ProjectFinder.MAX_FILE_BYTES, this::isCancelled);
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    applyResult(get(), null);
                } catch (Exception ex) {
                    applyResult(null, "Search failed: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    /**
     * Runs the search on the current thread so tests do not wait for a worker.
     */
    void searchNow() {
        setMinimized(false);
        String query = queryField.getText();
        if (projectRoot == null) {
            applyResult(null, "Open a project directory first");
            return;
        }
        if (query == null || query.isEmpty()) {
            applyResult(null, "Enter search text");
            return;
        }
        applyResult(ProjectFinder.search(projectRoot, query, matchCase.isSelected()), null);
    }

    void openSelected() {
        ProjectFinder.Match match = resultList.getSelectedValue();
        if (match != null) {
            openMatchHandler.accept(match);
        }
    }

    void selectResult(int index) {
        if (index >= 0 && index < listModel.size()) {
            resultList.setSelectedIndex(index);
        }
    }

    JButton minimizeButton() {
        return minimizeButton;
    }

    private void applyResult(ProjectFinder.Result result, String error) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> applyResult(result, error));
            return;
        }
        listModel.clear();
        if (error != null) {
            statusLabel.setText(error);
            titleLabel.setText("Find in Files");
            return;
        }
        if (result == null) {
            statusLabel.setText(" ");
            return;
        }
        for (ProjectFinder.Match match : result.matches()) {
            listModel.addElement(match);
        }
        if (!listModel.isEmpty()) {
            resultList.setSelectedIndex(0);
        }
        String count = result.size() + (result.size() == 1 ? " match" : " matches");
        if (result.truncated()) {
            count += " (truncated)";
        }
        statusLabel.setText(count + " in " + result.filesSearched() + " files");
        titleLabel.setText("Find in Files (" + result.size() + ")");
    }

    private void applySizes() {
        int titleHeight = Math.max(TITLE_HEIGHT, toolbar.getPreferredSize().height);
        if (minimized) {
            Dimension collapsed = new Dimension(200, titleHeight);
            setPreferredSize(collapsed);
            setMinimumSize(collapsed);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, titleHeight));
        } else {
            setPreferredSize(new Dimension(200, EXPANDED_HEIGHT));
            setMinimumSize(new Dimension(120, 120));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        }
    }
}
