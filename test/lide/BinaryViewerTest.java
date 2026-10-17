package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

/**
 * Tests for binary detection, hex dump formatting, and binary viewer tabs.
 */
public final class BinaryViewerTest {

    @Test
    void testDetectsNullByteAsBinary() throws Exception {
        byte[] data = new byte[] {'a', 'b', 0, 'c'};
        assertTrue(BinaryDetector.isBinary(data), "null byte is binary");
    }

    @Test
    void testTextIsNotBinary() throws Exception {
        byte[] data = "hello\r\nworld\t!".getBytes(StandardCharsets.UTF_8);
        assertTrue(!BinaryDetector.isBinary(data), "plain text not binary");
    }

    @Test
    void testHighControlRatioIsBinary() throws Exception {
        byte[] data = new byte[100];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 2 == 0 ? 0x01 : 0x02);
        }
        assertTrue(BinaryDetector.isBinary(data), "control-heavy is binary");
    }

    @Test
    void testHexDumpFormat() throws Exception {
        byte[] data = "Hello World!".getBytes(StandardCharsets.US_ASCII);
        String dump = HexDump.format(data);
        assertTrue(dump.startsWith("00000000"), "has offset");
        assertTrue(dump.contains("48 65 6C 6C 6F"), "has hex");
        assertTrue(dump.contains("|Hello World!|"), "has ascii");
    }

    @Test
    void testHexDumpTruncation() throws Exception {
        byte[] data = new byte[40];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        String dump = HexDump.format(data, 16);
        assertTrue(dump.contains("truncated after 16 of 40"), "truncated note");
        assertTrue(dump.indexOf("00000010") < 0, "only one full line of offsets");
    }

    @Test
    void testEncodingFailureHeuristic() throws Exception {
        assertTrue(EditorTabPane.looksLikeEncodingFailure(
                        new java.nio.charset.MalformedInputException(1)), "malformed message");
        assertTrue(!EditorTabPane.looksLikeEncodingFailure(new java.io.IOException("disk full")), "plain io not encoding");
    }

    @Test
    void testOpensBinaryTab() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testOpensBinaryTab);
            return;
        }
                    Path file = Files.createTempFile("lide-bin-", ".dat");
            try {
                Files.write(file, new byte[] {0x00, 0x01, 0x02, (byte) 0xFF, 'A'});
                EditorTabPane pane = new EditorTabPane();
                pane.openFile(file);
                assertTrue(pane.getActiveBinaryViewer() != null, "binary viewer active");
                assertTrue(pane.getActiveEditor() == null, "no text editor");
                BinaryViewer viewer = pane.getActiveBinaryViewer();
                assertTrue(viewer.getTitle().contains("[binary]"), "title marks binary");
                assertTrue(viewer.getHexText().contains("00 01 02 FF 41"), "hex shown");
            } finally {
                Files.deleteIfExists(file);
            }
    }

    @Test
    void testOpensTextTab() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testOpensTextTab);
            return;
        }
                    Path file = Files.createTempFile("lide-txt-", ".txt");
            try {
                Files.writeString(file, "hello text", StandardCharsets.UTF_8);
                EditorTabPane pane = new EditorTabPane();
                pane.openFile(file);
                assertTrue(pane.getActiveEditor() != null, "text editor active");
                assertTrue(pane.getActiveBinaryViewer() == null, "no binary viewer");
                assertEquals("hello text", normalize(pane.getActiveEditor().getText()), "text content");
            } finally {
                Files.deleteIfExists(file);
            }
    }

    @Test
    void testOpensKnownTextDespiteLatin1() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testOpensKnownTextDespiteLatin1);
            return;
        }
                    Path file = Files.createTempFile("lide-csv-", ".csv");
            try {
                Files.write(file, new byte[] {'a', ',', (byte) 0xE9, '\n'});
                EditorTabPane pane = new EditorTabPane();
                pane.openFile(file);
                assertTrue(pane.getActiveEditor() != null, "csv opens as text");
                assertTrue(pane.getActiveBinaryViewer() == null, "csv not hex");
                String text = normalize(pane.getActiveEditor().getText());
                assertEquals("a,\u00E9", text.strip(), "latin1 csv");
            } finally {
                Files.deleteIfExists(file);
            }
    }

    @Test
    void testOpensUtf16TextFile() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testOpensUtf16TextFile);
            return;
        }
                    Path file = Files.createTempFile("lide-md-", ".md");
            try {
                Files.write(file, new byte[] {
                        (byte) 0xFF, (byte) 0xFE,
                        '#', 0, ' ', 0, 'H', 0, 'i', 0
                });
                EditorTabPane pane = new EditorTabPane();
                pane.openFile(file);
                assertTrue(pane.getActiveEditor() != null, "md opens as text");
                assertTrue(pane.getActiveBinaryViewer() == null, "md not hex");
                assertEquals("# Hi", normalize(pane.getActiveEditor().getText()), "utf16 md");
            } finally {
                Files.deleteIfExists(file);
            }
    }

    @Test
    void testKnownBinaryOpensAsHex() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testKnownBinaryOpensAsHex);
            return;
        }
                    Path file = Files.createTempFile("lide-png-", ".png");
            try {
                Files.write(file, "not really a png".getBytes(StandardCharsets.US_ASCII));
                EditorTabPane pane = new EditorTabPane();
                pane.openFile(file);
                assertTrue(pane.getActiveBinaryViewer() != null, "png opens as binary");
                assertTrue(pane.getActiveEditor() == null, "png not text editor");
            } finally {
                Files.deleteIfExists(file);
            }
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

}
