package lide;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.undo.UndoManager;

/**
 * Code editor with line numbers and deferred syntax highlighting.
 */
public final class CodeEditor extends JPanel {
    private final JTextPane textPane;
    private final LineNumberGutter gutter;
    private final SyntaxHighlighter highlighter = new SyntaxHighlighter();
    private final UndoManager undoManager = new UndoManager();
    private final AtomicBoolean highlightPending = new AtomicBoolean(false);

    private Path filePath;
    private Language language = Language.PLAIN;
    private boolean dirty;
    private boolean applyingHighlight;
    private boolean controlDown;
    private Runnable dirtyListener;
    private Supplier<Path> projectRootSupplier = () -> null;
    private Consumer<ClassNavigator.Target> navigateHandler = target -> {
    };

    public CodeEditor() {
        super(new BorderLayout());
        setBackground(IdeTheme.BG_EDITOR);

        textPane = new JTextPane();
        textPane.setFont(IdeTheme.EDITOR_FONT);
        textPane.setBackground(IdeTheme.BG_EDITOR);
        textPane.setForeground(IdeTheme.DEFAULT_TEXT);
        textPane.setCaretColor(IdeTheme.CARET);
        textPane.setSelectionColor(IdeTheme.SELECTION);
        textPane.setSelectedTextColor(java.awt.Color.WHITE);
        textPane.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8));
        // Keep find matches visible while the find bar (not the editor) has focus.
        DefaultCaret caret = new DefaultCaret() {
            @Override
            public void setSelectionVisible(boolean visible) {
                super.setSelectionVisible(true);
            }
        };
        caret.setBlinkRate(textPane.getCaret().getBlinkRate());
        textPane.setCaret(caret);
        configureTabs(textPane);

        gutter = new LineNumberGutter(textPane);

        JScrollPane scroll = new JScrollPane(textPane);
        scroll.setRowHeaderView(gutter);
        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(IdeTheme.BG_EDITOR);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        textPane.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onEdit();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onEdit();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // Attribute changes from highlighting — ignore.
            }
        });
        textPane.getDocument().addUndoableEditListener(new UndoableEditListener() {
            @Override
            public void undoableEditHappened(UndoableEditEvent e) {
                if (!applyingHighlight) {
                    undoManager.addEdit(e.getEdit());
                }
            }
        });

        textPane.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_TAB && !e.isShiftDown()) {
                    e.consume();
                    textPane.replaceSelection("    ");
                }
                if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
                    controlDown = true;
                    updateNavigateCursor(textPane.getMousePosition());
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
                    controlDown = false;
                    textPane.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                }
            }
        });

        MouseAdapter navigation = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || !e.isControlDown()) {
                    return;
                }
                navigateAt(e.getPoint());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                controlDown = e.isControlDown();
                updateNavigateCursor(e.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                textPane.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
            }
        };
        textPane.addMouseListener(navigation);
        textPane.addMouseMotionListener(navigation);
    }

    public void setProjectRootSupplier(Supplier<Path> projectRootSupplier) {
        this.projectRootSupplier = projectRootSupplier;
    }

    public void setNavigateHandler(Consumer<ClassNavigator.Target> navigateHandler) {
        this.navigateHandler = navigateHandler;
    }

    private void navigateAt(Point point) {
        int offset = textPane.viewToModel2D(point);
        Optional<ClassNavigator.Target> target = ClassNavigator.resolve(
                projectRootSupplier.get(),
                filePath,
                getDocumentText(),
                language,
                offset);
        target.ifPresent(navigateHandler);
    }

    private void updateNavigateCursor(Point point) {
        if (point == null || !controlDown) {
            textPane.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
            return;
        }
        int offset = textPane.viewToModel2D(point);
        String word = ClassNavigator.identifierAt(getDocumentText(), offset);
        if (ClassNavigator.isNavigableClassName(word, language)) {
            textPane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            textPane.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        }
    }

    private static void configureTabs(JTextPane pane) {
        FontMetrics fm = pane.getFontMetrics(IdeTheme.EDITOR_FONT);
        int tabWidth = fm.charWidth(' ') * 4;
        TabStop[] stops = new TabStop[32];
        for (int i = 0; i < stops.length; i++) {
            stops[i] = new TabStop((i + 1) * tabWidth);
        }
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setTabSet(attrs, new TabSet(stops));
        pane.setParagraphAttributes(attrs, false);
    }

    public void openFile(Path path) throws Exception {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        filePath = path;
        language = Language.fromPath(path);
        applyingHighlight = true;
        try {
            textPane.setText(content);
            textPane.setCaretPosition(0);
        } finally {
            applyingHighlight = false;
        }
        undoManager.discardAllEdits();
        dirty = false;
        scheduleHighlight();
        gutter.repaint();
        notifyDirty();
    }

    public void goToOffset(int offset) {
        int length = textPane.getDocument().getLength();
        int caret = Math.max(0, Math.min(offset, length));
        textPane.setCaretPosition(caret);
        textPane.requestFocusInWindow();
        try {
            var shape = textPane.modelToView2D(caret);
            if (shape != null) {
                textPane.scrollRectToVisible(shape.getBounds());
            }
        } catch (BadLocationException ignored) {
            // Caret is still set.
        }
    }

    public void setPlainContent(String content, Language language) {
        this.filePath = null;
        this.language = language;
        applyingHighlight = true;
        try {
            textPane.setText(content);
            textPane.setCaretPosition(0);
        } finally {
            applyingHighlight = false;
        }
        undoManager.discardAllEdits();
        dirty = false;
        scheduleHighlight();
        gutter.repaint();
        notifyDirty();
    }

    public boolean canUndo() {
        return undoManager.canUndo();
    }

    public boolean canRedo() {
        return undoManager.canRedo();
    }

    public void undo() {
        if (undoManager.canUndo()) {
            undoManager.undo();
        }
    }

    public void redo() {
        if (undoManager.canRedo()) {
            undoManager.redo();
        }
    }

    public void copy() {
        textPane.copy();
    }

    public void paste() {
        textPane.paste();
    }

    public String getText() {
        return textPane.getText();
    }

    /**
     * Text as stored in the document model (LF newlines). Use this with caret,
     * selection, and viewToModel offsets — {@link JTextPane#getText()} may rewrite
     * newlines to the platform separator and no longer match those offsets.
     */
    public String getDocumentText() {
        try {
            Document doc = textPane.getDocument();
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException ex) {
            return "";
        }
    }

    public String getSelectedText() {
        return textPane.getSelectedText();
    }

    public int getCaretPosition() {
        return textPane.getCaretPosition();
    }

    public int getSelectionStart() {
        return textPane.getSelectionStart();
    }

    public int getSelectionEnd() {
        return textPane.getSelectionEnd();
    }

    public void selectRange(int start, int end) {
        int length = textPane.getDocument().getLength();
        int from = Math.max(0, Math.min(start, length));
        int to = Math.max(0, Math.min(end, length));
        // Do not request focus: find-as-you-type must keep the find bar focused.
        textPane.setCaretPosition(from);
        textPane.moveCaretPosition(to);
        textPane.getCaret().setSelectionVisible(true);
        try {
            var shape = textPane.modelToView2D(from);
            if (shape != null) {
                textPane.scrollRectToVisible(shape.getBounds());
            }
        } catch (BadLocationException ignored) {
            // Selection is still applied.
        }
    }

    public void save() throws Exception {
        if (filePath == null) {
            return;
        }
        Files.writeString(filePath, textPane.getText(), StandardCharsets.UTF_8);
        dirty = false;
        notifyDirty();
    }

    public Path getFilePath() {
        return filePath;
    }

    void setFilePath(Path path) {
        if (path == null) {
            return;
        }
        filePath = path.toAbsolutePath().normalize();
        language = Language.fromPath(filePath);
        notifyDirty();
        scheduleHighlight();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener;
    }

    public JTextPane getTextPane() {
        return textPane;
    }

    public String getTitle() {
        String name = filePath != null ? filePath.getFileName().toString() : "Untitled";
        return dirty ? name + " *" : name;
    }

    private void onEdit() {
        if (applyingHighlight) {
            return;
        }
        if (!dirty) {
            dirty = true;
            notifyDirty();
        }
        gutter.repaint();
        scheduleHighlight();
    }

    private void notifyDirty() {
        if (dirtyListener != null) {
            dirtyListener.run();
        }
    }

    private void scheduleHighlight() {
        if (!highlightPending.compareAndSet(false, true)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            highlightPending.set(false);
            rehighlight();
        });
    }

    private void rehighlight() {
        applyingHighlight = true;
        try {
            StyledDocument doc = textPane.getStyledDocument();
            int caret = textPane.getCaretPosition();
            highlighter.highlight(doc, language);
            textPane.setCaretPosition(Math.min(caret, doc.getLength()));
        } finally {
            applyingHighlight = false;
        }
        gutter.repaint();
    }

    /**
     * Left gutter painting line numbers.
     */
    private static final class LineNumberGutter extends JPanel {
        private final JTextPane textPane;

        LineNumberGutter(JTextPane textPane) {
            this.textPane = textPane;
            setBackground(IdeTheme.LINE_GUTTER);
            setForeground(IdeTheme.LINE_NUMBER);
            setFont(IdeTheme.EDITOR_FONT);
            setPreferredSize(new Dimension(48, 10));
            setBorder(javax.swing.BorderFactory.createMatteBorder(0, 0, 0, 1, IdeTheme.BORDER));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(IdeTheme.LINE_GUTTER);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(IdeTheme.LINE_NUMBER);
            g.setFont(IdeTheme.EDITOR_FONT);

            FontMetrics fm = g.getFontMetrics();
            StyledDocument doc = textPane.getStyledDocument();
            Element root = doc.getDefaultRootElement();
            int lineCount = root.getElementCount();

            Rectangle clip = g.getClipBounds();
            int startOffset = textPane.viewToModel2D(new java.awt.geom.Point2D.Double(0, clip.y));
            int endOffset = textPane.viewToModel2D(
                    new java.awt.geom.Point2D.Double(0, clip.y + clip.height));
            int startLine = root.getElementIndex(Math.max(startOffset, 0));
            int endLine = root.getElementIndex(Math.max(endOffset, 0));

            int widthNeeded = Math.max(48, fm.stringWidth(String.valueOf(lineCount)) + 16);
            if (getPreferredSize().width != widthNeeded) {
                setPreferredSize(new Dimension(widthNeeded, 10));
                revalidate();
            }

            for (int line = startLine; line <= endLine && line < lineCount; line++) {
                try {
                    Element el = root.getElement(line);
                    Rectangle r = textPane.modelToView2D(el.getStartOffset()).getBounds();
                    String label = String.valueOf(line + 1);
                    int x = getWidth() - fm.stringWidth(label) - 8;
                    int y = r.y + fm.getAscent();
                    g.drawString(label, x, y);
                } catch (BadLocationException ignored) {
                    // Skip this line.
                }
            }
        }
    }
}
