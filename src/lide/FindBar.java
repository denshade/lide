package lide;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Inline find bar for searching within the active editor.
 */
public final class FindBar extends JPanel {
    private final JTextField queryField = new JTextField(24);
    private final JCheckBox matchCase = new JCheckBox("Match case");
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton nextButton = new JButton("Next");
    private final JButton prevButton = new JButton("Previous");
    private final JButton closeButton = new JButton("×");

    private Runnable onNext = () -> {
    };
    private Runnable onPrevious = () -> {
    };
    private Runnable onQueryChanged = () -> {
    };
    private Runnable onClose = () -> {
    };

    public FindBar() {
        super(new BorderLayout(8, 0));
        setBackground(IdeTheme.BG_RAISED);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, IdeTheme.BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JLabel findLabel = new JLabel("Find:");
        findLabel.setForeground(IdeTheme.FG);
        findLabel.setFont(IdeTheme.UI_FONT);

        queryField.setFont(IdeTheme.UI_FONT);
        queryField.setBackground(IdeTheme.BG_EDITOR);
        queryField.setForeground(IdeTheme.FG);
        queryField.setCaretColor(IdeTheme.CARET);
        queryField.setSelectionColor(IdeTheme.SELECTION);
        queryField.setSelectedTextColor(Color.WHITE);
        queryField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(IdeTheme.BORDER),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));

        matchCase.setForeground(IdeTheme.FG);
        matchCase.setBackground(IdeTheme.BG_RAISED);
        matchCase.setFont(IdeTheme.UI_FONT);
        matchCase.setFocusable(false);

        statusLabel.setForeground(IdeTheme.FG_DIM);
        statusLabel.setFont(IdeTheme.UI_FONT.deriveFont(12f));

        styleButton(nextButton);
        styleButton(prevButton);
        styleButton(closeButton);
        closeButton.setPreferredSize(new Dimension(28, 24));
        closeButton.setToolTipText("Close (Esc)");

        nextButton.addActionListener(e -> onNext.run());
        prevButton.addActionListener(e -> onPrevious.run());
        closeButton.addActionListener(e -> onClose.run());
        matchCase.addActionListener(e -> onQueryChanged.run());

        queryField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onQueryChanged.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onQueryChanged.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onQueryChanged.run();
            }
        });

        queryField.addActionListener(e -> onNext.run());

        queryField.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-find");
        queryField.getActionMap().put("close-find", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onClose.run();
            }
        });
        queryField.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "find-prev");
        queryField.getActionMap().put("find-prev", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onPrevious.run();
            }
        });

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(findLabel);
        left.add(queryField);
        left.add(prevButton);
        left.add(nextButton);
        left.add(matchCase);
        left.add(statusLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(closeButton);

        add(left, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
        setVisible(false);
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

    public void setOnNext(Runnable onNext) {
        this.onNext = onNext;
    }

    public void setOnPrevious(Runnable onPrevious) {
        this.onPrevious = onPrevious;
    }

    public void setOnQueryChanged(Runnable onQueryChanged) {
        this.onQueryChanged = onQueryChanged;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
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

    public void setStatus(String status) {
        statusLabel.setText(status == null || status.isBlank() ? " " : status);
    }

    public void focusQuery() {
        queryField.requestFocusInWindow();
        queryField.selectAll();
    }
}
