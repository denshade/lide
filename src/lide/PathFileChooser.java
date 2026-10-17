package lide;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * File chooser with a path field so a pasted path can be opened directly.
 * Clicking through folders still uses the standard chooser. Enter in the path
 * field, or Open after the field has been edited, uses the pasted path.
 */
final class PathFileChooser extends JFileChooser {
    private final JTextField pathField = new JTextField();
    private final List<ActionListener> stockApproveListeners = new ArrayList<>();
    private boolean syncing;
    private boolean pathEdited;
    private boolean applying;
    private boolean approveHooked;

    PathFileChooser(int selectionMode) {
        setFileSelectionMode(selectionMode);
        pathField.setName("path");
        pathField.setFont(IdeTheme.UI_FONT);
        pathField.setBackground(IdeTheme.BG_EDITOR);
        pathField.setForeground(IdeTheme.FG);
        pathField.setCaretColor(IdeTheme.CARET);
        pathField.setSelectionColor(IdeTheme.SELECTION);
        pathField.setSelectedTextColor(Color.WHITE);
        pathField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(IdeTheme.BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        pathField.setToolTipText("Paste a path and press Enter");
        pathField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markEdited();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markEdited();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                markEdited();
            }
        });
        pathField.addActionListener(e -> applyTypedPath());
        addPropertyChangeListener(e -> followChooser(e.getPropertyName()));
        showCurrentPath();
    }

    JTextField pathField() {
        return pathField;
    }

    @Override
    protected JDialog createDialog(Component parent) throws HeadlessException {
        JDialog dialog = super.createDialog(parent);
        Container content = dialog.getContentPane();
        content.remove(this);

        JLabel label = new JLabel("Path");
        label.setForeground(IdeTheme.FG);
        label.setFont(IdeTheme.UI_FONT);
        label.setLabelFor(pathField);

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(IdeTheme.BG);
        row.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));
        row.add(label, BorderLayout.WEST);
        row.add(pathField, BorderLayout.CENTER);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(IdeTheme.BG);
        wrap.add(row, BorderLayout.NORTH);
        wrap.add(this, BorderLayout.CENTER);
        content.add(wrap, BorderLayout.CENTER);

        hookApproveButton();
        showCurrentPath();
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                SwingUtilities.invokeLater(() -> {
                    pathField.requestFocusInWindow();
                    pathField.selectAll();
                });
            }
        });
        return dialog;
    }

    /**
     * What to do with text from the path field.
     * {@code NAVIGATE} moves the chooser without closing it: a folder pasted
     * into a file chooser, or a file pasted into a directory chooser (its parent).
     */
    static Choice choose(String raw, Path currentDirectory, int selectionMode) {
        String text = stripDecorations(raw);
        if (text.isEmpty()) {
            if (selectionMode == DIRECTORIES_ONLY
                    && currentDirectory != null
                    && Files.isDirectory(currentDirectory)) {
                return Choice.approve(currentDirectory.toAbsolutePath().normalize());
            }
            return Choice.reject();
        }
        Path path = parsePath(text, currentDirectory);
        if (path == null) {
            return Choice.reject();
        }
        path = path.toAbsolutePath().normalize();
        if (Files.isDirectory(path)) {
            if (selectionMode == FILES_ONLY) {
                return Choice.navigate(path);
            }
            return Choice.approve(path);
        }
        if (Files.isRegularFile(path)) {
            if (selectionMode == DIRECTORIES_ONLY) {
                Path parent = path.getParent();
                if (parent == null) {
                    return Choice.reject();
                }
                return Choice.navigate(parent);
            }
            return Choice.approve(path);
        }
        return Choice.reject();
    }

    static String stripDecorations(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        int newline = text.indexOf('\n');
        if (newline >= 0) {
            text = text.substring(0, newline);
        }
        text = text.trim();
        if (text.length() >= 2) {
            char first = text.charAt(0);
            char last = text.charAt(text.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                text = text.substring(1, text.length() - 1).trim();
            }
        }
        return text;
    }

    void applyTypedPath() {
        if (applying) {
            return;
        }
        applying = true;
        try {
            Path current = getCurrentDirectory() == null ? null : getCurrentDirectory().toPath();
            Choice choice = choose(pathField.getText(), current, getFileSelectionMode());
            if (choice.action == Choice.Action.NAVIGATE) {
                pathEdited = false;
                setCurrentDirectory(choice.path.toFile());
                setPathQuietly(choice.path.toString());
                pathField.selectAll();
                pathField.requestFocusInWindow();
                return;
            }
            if (choice.action == Choice.Action.REJECT) {
                Toolkit.getDefaultToolkit().beep();
                pathField.selectAll();
                pathField.requestFocusInWindow();
                return;
            }
            pathEdited = false;
            syncing = true;
            try {
                setSelectedFile(choice.path.toFile());
                super.approveSelection();
            } finally {
                syncing = false;
            }
        } finally {
            applying = false;
        }
    }

    private void followChooser(String property) {
        if (syncing || pathEdited || pathField.isFocusOwner()) {
            return;
        }
        if (SELECTED_FILE_CHANGED_PROPERTY.equals(property)
                || DIRECTORY_CHANGED_PROPERTY.equals(property)) {
            showCurrentPath();
        }
    }

    private void showCurrentPath() {
        if (pathEdited) {
            return;
        }
        File selected = getSelectedFile();
        if (selected != null
                && ((isFileSelectionEnabled() && !selected.isDirectory())
                || (isDirectorySelectionEnabled() && selected.isDirectory()))) {
            setPathQuietly(selected.getAbsolutePath());
            return;
        }
        File dir = getCurrentDirectory();
        if (dir != null) {
            setPathQuietly(dir.getAbsolutePath());
        }
    }

    private void setPathQuietly(String text) {
        syncing = true;
        try {
            pathField.setText(text == null ? "" : text);
        } finally {
            syncing = false;
        }
    }

    private void markEdited() {
        if (!syncing) {
            pathEdited = true;
        }
    }

    private void hookApproveButton() {
        if (approveHooked) {
            return;
        }
        JButton button = findApproveButton();
        if (button == null) {
            return;
        }
        approveHooked = true;
        stockApproveListeners.clear();
        for (ActionListener listener : button.getActionListeners()) {
            stockApproveListeners.add(listener);
            button.removeActionListener(listener);
        }
        button.addActionListener(e -> {
            if (pathEdited) {
                applyTypedPath();
            } else {
                for (ActionListener listener : stockApproveListeners) {
                    listener.actionPerformed(e);
                }
            }
        });
    }

    private JButton findApproveButton() {
        List<JButton> buttons = new ArrayList<>();
        collectButtons(this, buttons);
        String approve = approveButtonLabel();
        for (JButton button : buttons) {
            if (approve != null && approve.equals(button.getText())) {
                return button;
            }
        }
        for (JButton button : buttons) {
            String text = button.getText();
            if (text != null && !text.isBlank() && !"Cancel".equalsIgnoreCase(text)) {
                return button;
            }
        }
        return null;
    }

    private String approveButtonLabel() {
        if (getUI() instanceof javax.swing.plaf.basic.BasicFileChooserUI ui) {
            String text = ui.getApproveButtonText(this);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        String text = getApproveButtonText();
        return text == null || text.isBlank() ? "Open" : text;
    }

    private static void collectButtons(Component component, List<JButton> buttons) {
        if (component instanceof JButton button) {
            buttons.add(button);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectButtons(child, buttons);
            }
        }
    }

    private static Path parsePath(String text, Path currentDirectory) {
        if (text.regionMatches(true, 0, "file:", 0, 5)) {
            try {
                return Path.of(URI.create(text));
            } catch (IllegalArgumentException ex) {
                // Not a usable file URI; try it as a normal path below.
            }
        }
        try {
            Path path = Path.of(text);
            if (!path.isAbsolute() && currentDirectory != null) {
                path = currentDirectory.resolve(path);
            }
            return path;
        } catch (InvalidPathException ex) {
            return null;
        }
    }

    static final class Choice {
        enum Action {
            APPROVE,
            NAVIGATE,
            REJECT
        }

        final Action action;
        final Path path;

        private Choice(Action action, Path path) {
            this.action = action;
            this.path = path;
        }

        static Choice approve(Path path) {
            return new Choice(Action.APPROVE, path);
        }

        static Choice navigate(Path path) {
            return new Choice(Action.NAVIGATE, path);
        }

        static Choice reject() {
            return new Choice(Action.REJECT, null);
        }
    }
}
