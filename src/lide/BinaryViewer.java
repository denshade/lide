package lide;

import java.awt.BorderLayout;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

/**
 * Read-only hex dump viewer for binary files.
 */
public final class BinaryViewer extends JPanel {
    private final JTextArea hexArea = new JTextArea();
    private final JLabel header = new JLabel();
    private Path filePath;
    private long fileSize;
    private int displayedBytes;

    public BinaryViewer() {
        super(new BorderLayout());
        setBackground(IdeTheme.BG_EDITOR);

        header.setFont(IdeTheme.UI_FONT);
        header.setForeground(IdeTheme.FG_DIM);
        header.setBorder(new EmptyBorder(6, 10, 6, 10));
        header.setOpaque(true);
        header.setBackground(IdeTheme.BG_RAISED);

        hexArea.setEditable(false);
        hexArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        hexArea.setBackground(IdeTheme.BG_EDITOR);
        hexArea.setForeground(IdeTheme.DEFAULT_TEXT);
        hexArea.setCaretColor(IdeTheme.CARET);
        hexArea.setSelectionColor(IdeTheme.SELECTION);
        hexArea.setSelectedTextColor(java.awt.Color.WHITE);
        hexArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JScrollPane scroll = new JScrollPane(hexArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(IdeTheme.BG_EDITOR);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    public void openFile(Path path) throws Exception {
        byte[] all = Files.readAllBytes(path);
        filePath = path.toAbsolutePath().normalize();
        fileSize = all.length;
        displayedBytes = Math.min(all.length, HexDump.DEFAULT_MAX_BYTES);
        hexArea.setText(HexDump.format(all));
        hexArea.setCaretPosition(0);
        String name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
        String trunc = displayedBytes < fileSize
                ? " (showing first " + displayedBytes + " bytes)"
                : "";
        header.setText("Binary file — " + name + " — " + fileSize + " bytes" + trunc);
    }

    public void setContent(Path path, byte[] data) {
        filePath = path == null ? null : path.toAbsolutePath().normalize();
        fileSize = data == null ? 0 : data.length;
        displayedBytes = data == null ? 0 : Math.min(data.length, HexDump.DEFAULT_MAX_BYTES);
        hexArea.setText(HexDump.format(data));
        hexArea.setCaretPosition(0);
        String name = filePath != null && filePath.getFileName() != null
                ? filePath.getFileName().toString()
                : "binary";
        header.setText("Binary file — " + name + " — " + fileSize + " bytes");
    }

    public Path getFilePath() {
        return filePath;
    }

    void setFilePath(Path path) {
        if (path == null) {
            return;
        }
        filePath = path.toAbsolutePath().normalize();
        String name = filePath.getFileName() != null
                ? filePath.getFileName().toString()
                : filePath.toString();
        String trunc = displayedBytes < fileSize
                ? " (showing first " + displayedBytes + " bytes)"
                : "";
        header.setText("Binary file — " + name + " — " + fileSize + " bytes" + trunc);
    }

    public String getTitle() {
        if (filePath == null) {
            return "Binary";
        }
        Path name = filePath.getFileName();
        return (name != null ? name.toString() : filePath.toString()) + " [binary]";
    }

    public long getFileSize() {
        return fileSize;
    }

    public int getDisplayedBytes() {
        return displayedBytes;
    }

    public String getHexText() {
        return hexArea.getText();
    }
}
