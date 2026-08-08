package lide;

import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

/**
 * Tests for binary detection, hex dump formatting, and binary viewer tabs.
 */
public final class BinaryViewerTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testDetectsNullByteAsBinary();
        testTextIsNotBinary();
        testHighControlRatioIsBinary();
        testHexDumpFormat();
        testHexDumpTruncation();
        testEncodingFailureHeuristic();
        if (!GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeAndWait(BinaryViewerTest::testOpensBinaryTab);
            SwingUtilities.invokeAndWait(BinaryViewerTest::testOpensTextTab);
        }
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testDetectsNullByteAsBinary() {
        byte[] data = new byte[] {'a', 'b', 0, 'c'};
        assertTrue("null byte is binary", BinaryDetector.isBinary(data));
    }

    private static void testTextIsNotBinary() {
        byte[] data = "hello\r\nworld\t!".getBytes(StandardCharsets.UTF_8);
        assertTrue("plain text not binary", !BinaryDetector.isBinary(data));
    }

    private static void testHighControlRatioIsBinary() {
        byte[] data = new byte[100];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 2 == 0 ? 0x01 : 0x02);
        }
        assertTrue("control-heavy is binary", BinaryDetector.isBinary(data));
    }

    private static void testHexDumpFormat() {
        byte[] data = "Hello World!".getBytes(StandardCharsets.US_ASCII);
        String dump = HexDump.format(data);
        assertTrue("has offset", dump.startsWith("00000000"));
        assertTrue("has hex", dump.contains("48 65 6C 6C 6F"));
        assertTrue("has ascii", dump.contains("|Hello World!|"));
    }

    private static void testHexDumpTruncation() {
        byte[] data = new byte[40];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        String dump = HexDump.format(data, 16);
        assertTrue("truncated note", dump.contains("truncated after 16 of 40"));
        assertTrue("only one full line of offsets", dump.indexOf("00000010") < 0);
    }

    private static void testEncodingFailureHeuristic() {
        assertTrue("malformed message",
                EditorTabPane.looksLikeEncodingFailure(
                        new java.nio.charset.MalformedInputException(1)));
        assertTrue("plain io not encoding",
                !EditorTabPane.looksLikeEncodingFailure(new java.io.IOException("disk full")));
    }

    private static void testOpensBinaryTab() {
        try {
            Path file = Files.createTempFile("lide-bin-", ".dat");
            try {
                Files.write(file, new byte[] {0x00, 0x01, 0x02, (byte) 0xFF, 'A'});
                EditorTabPane pane = new EditorTabPane();
                pane.openFile(file);
                assertTrue("binary viewer active", pane.getActiveBinaryViewer() != null);
                assertTrue("no text editor", pane.getActiveEditor() == null);
                BinaryViewer viewer = pane.getActiveBinaryViewer();
                assertTrue("title marks binary", viewer.getTitle().contains("[binary]"));
                assertTrue("hex shown", viewer.getHexText().contains("00 01 02 FF 41"));
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (Exception ex) {
            failed++;
            System.err.println("FAIL opens binary tab: " + ex);
        }
    }

    private static void testOpensTextTab() {
        try {
            Path file = Files.createTempFile("lide-txt-", ".txt");
            try {
                Files.writeString(file, "hello text", StandardCharsets.UTF_8);
                EditorTabPane pane = new EditorTabPane();
                pane.openFile(file);
                assertTrue("text editor active", pane.getActiveEditor() != null);
                assertTrue("no binary viewer", pane.getActiveBinaryViewer() == null);
                assertEqual("text content", "hello text",
                        normalize(pane.getActiveEditor().getText()));
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (Exception ex) {
            failed++;
            System.err.println("FAIL opens text tab: " + ex);
        }
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static void assertEqual(String label, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
            return;
        }
        failed++;
        System.err.println("FAIL " + label + ": expected " + expected + " but was " + actual);
    }

    private static void assertTrue(String label, boolean condition) {
        if (condition) {
            passed++;
            return;
        }
        failed++;
        System.err.println("FAIL " + label);
    }

    private BinaryViewerTest() {
    }
}
