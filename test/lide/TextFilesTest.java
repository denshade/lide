package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Tests for known text/binary extensions and text decoding.
 */
public final class TextFilesTest {

    @Test
    void testKnownTextExtensions() throws Exception {
        assertTrue(TextFiles.isKnownText(Path.of("README.md")), ".md");
        assertTrue(TextFiles.isKnownText(Path.of("notes.txt")), ".txt");
        assertTrue(TextFiles.isKnownText(Path.of("data.csv")), ".csv");
        assertTrue(TextFiles.isKnownText(Path.of("package.json")), ".json");
        assertTrue(TextFiles.isKnownText(Path.of("app.yml")), ".yml");
        assertTrue(TextFiles.isKnownText(Path.of("Main.java")), ".java");
        assertTrue(TextFiles.isKnownText(Path.of("run.bat")), ".bat");
        assertTrue(TextFiles.isKnownText(Path.of("NOTES.TXT")), "case insensitive");
    }

    @Test
    void testKnownTextFilenames() throws Exception {
        assertTrue(TextFiles.isKnownText(Path.of("Makefile")), "Makefile");
        assertTrue(TextFiles.isKnownText(Path.of("Dockerfile")), "Dockerfile");
        assertTrue(TextFiles.isKnownText(Path.of("LICENSE")), "LICENSE");
    }

    @Test
    void testUnknownAndBinaryExtensions() throws Exception {
        assertTrue(!TextFiles.isKnownText(Path.of("blob.xyz")), "unknown not known text");
        assertTrue(!TextFiles.isKnownText(Path.of("icon.png")), "png not text");
        assertTrue(TextFiles.isKnownBinary(Path.of("icon.png")), "png is binary");
        assertTrue(TextFiles.isKnownBinary(Path.of("lib.jar")), "jar is binary");
        assertTrue(TextFiles.isKnownBinary(Path.of("Foo.class")), "class is binary");
        assertTrue(!TextFiles.isKnownBinary(Path.of("a.txt")), "txt is not binary type");
        assertTrue(!TextFiles.isKnownBinary(Path.of("LICENSE")), "no extension not binary");
    }

    @Test
    void testDecodeUtf8() throws Exception {
        byte[] bytes = "hello café".getBytes(StandardCharsets.UTF_8);
        assertEquals("hello café", TextFiles.decode(bytes), "utf8");
    }

    @Test
    void testDecodeUtf8Bom() throws Exception {
        byte[] body = "hi".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[3 + body.length];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(body, 0, bytes, 3, body.length);
        assertEquals("hi", TextFiles.decode(bytes), "utf8 bom stripped");
    }

    @Test
    void testDecodeUtf16Le() throws Exception {
        byte[] bytes = new byte[] {
                (byte) 0xFF, (byte) 0xFE,
                'A', 0, 'B', 0, 'C', 0
        };
        assertEquals("ABC", TextFiles.decode(bytes), "utf16le");
    }

    @Test
    void testDecodeWindows1252() throws Exception {
        // 0xE9 is é in Windows-1252 and is not valid UTF-8 as a standalone byte.
        byte[] bytes = new byte[] {'c', 'a', 'f', (byte) 0xE9};
        String decoded = TextFiles.decode(bytes);
        assertEquals("caf" + '\u00E9', decoded, "windows-1252");
        assertTrue(!decoded.contains("\uFFFD"), "not replacement char");
    }

    @Test
    void testDecodeEmpty() throws Exception {
        assertEquals("", TextFiles.decode(new byte[0]), "empty");
        assertEquals("", TextFiles.decode(null), "null");
    }

}
