package lide;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Tests for known text/binary extensions and text decoding.
 */
public final class TextFilesTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        testKnownTextExtensions();
        testKnownTextFilenames();
        testUnknownAndBinaryExtensions();
        testDecodeUtf8();
        testDecodeUtf8Bom();
        testDecodeUtf16Le();
        testDecodeWindows1252();
        testDecodeEmpty();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testKnownTextExtensions() {
        assertTrue(".md", TextFiles.isKnownText(Path.of("README.md")));
        assertTrue(".txt", TextFiles.isKnownText(Path.of("notes.txt")));
        assertTrue(".csv", TextFiles.isKnownText(Path.of("data.csv")));
        assertTrue(".json", TextFiles.isKnownText(Path.of("package.json")));
        assertTrue(".yml", TextFiles.isKnownText(Path.of("app.yml")));
        assertTrue(".java", TextFiles.isKnownText(Path.of("Main.java")));
        assertTrue(".bat", TextFiles.isKnownText(Path.of("run.bat")));
        assertTrue("case insensitive", TextFiles.isKnownText(Path.of("NOTES.TXT")));
    }

    private static void testKnownTextFilenames() {
        assertTrue("Makefile", TextFiles.isKnownText(Path.of("Makefile")));
        assertTrue("Dockerfile", TextFiles.isKnownText(Path.of("Dockerfile")));
        assertTrue("LICENSE", TextFiles.isKnownText(Path.of("LICENSE")));
    }

    private static void testUnknownAndBinaryExtensions() {
        assertTrue("unknown not known text", !TextFiles.isKnownText(Path.of("blob.xyz")));
        assertTrue("png not text", !TextFiles.isKnownText(Path.of("icon.png")));
        assertTrue("png is binary", TextFiles.isKnownBinary(Path.of("icon.png")));
        assertTrue("jar is binary", TextFiles.isKnownBinary(Path.of("lib.jar")));
        assertTrue("class is binary", TextFiles.isKnownBinary(Path.of("Foo.class")));
        assertTrue("txt is not binary type", !TextFiles.isKnownBinary(Path.of("a.txt")));
        assertTrue("no extension not binary", !TextFiles.isKnownBinary(Path.of("LICENSE")));
    }

    private static void testDecodeUtf8() {
        byte[] bytes = "hello café".getBytes(StandardCharsets.UTF_8);
        assertEqual("utf8", "hello café", TextFiles.decode(bytes));
    }

    private static void testDecodeUtf8Bom() {
        byte[] body = "hi".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[3 + body.length];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(body, 0, bytes, 3, body.length);
        assertEqual("utf8 bom stripped", "hi", TextFiles.decode(bytes));
    }

    private static void testDecodeUtf16Le() {
        byte[] bytes = new byte[] {
                (byte) 0xFF, (byte) 0xFE,
                'A', 0, 'B', 0, 'C', 0
        };
        assertEqual("utf16le", "ABC", TextFiles.decode(bytes));
    }

    private static void testDecodeWindows1252() {
        // 0xE9 is é in Windows-1252 and is not valid UTF-8 as a standalone byte.
        byte[] bytes = new byte[] {'c', 'a', 'f', (byte) 0xE9};
        String decoded = TextFiles.decode(bytes);
        assertEqual("windows-1252", "caf" + '\u00E9', decoded);
        assertTrue("not replacement char", !decoded.contains("\uFFFD"));
    }

    private static void testDecodeEmpty() {
        assertEqual("empty", "", TextFiles.decode(new byte[0]));
        assertEqual("null", "", TextFiles.decode(null));
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

    private TextFilesTest() {
    }
}
