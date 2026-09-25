package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for recording exceptions to the app log file.
 */
public final class AppLogTest {

    @Test
    void testDefaultLogFile() throws Exception {
        Path expected = Path.of(System.getProperty("user.home"), ".lide", "lide.log");
        assertEquals(expected, AppLog.defaultLogFile(), "default log file");
    }

    @Test
    void testWritesExceptionToFile() throws Exception {
        Path previous = AppLog.logFile();
        Path file = Files.createTempFile("lide-log-", ".log");
        Files.deleteIfExists(file);
        try {
            AppLog.setLogFile(file);
            AppLog.exception("Could not read folder", new IOException("permission denied"));
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue(text.contains("Could not read folder"), "contains context");
            assertTrue(text.contains("permission denied"), "contains message");
            assertTrue(text.contains("java.io.IOException"), "contains stack");
        } finally {
            AppLog.setLogFile(previous);
            Files.deleteIfExists(file);
        }
    }

    @Test
    void testCreatesMissingParentDirectory() throws Exception {
        Path previous = AppLog.logFile();
        Path dir = Files.createTempDirectory("lide-log-parent");
        Path file = dir.resolve("nested").resolve("lide.log");
        try {
            AppLog.setLogFile(file);
            AppLog.exception("save failed", new IOException("disk full"));
            assertTrue(Files.isRegularFile(file), "log created");
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue(text.contains("save failed"), "nested context");
        } finally {
            AppLog.setLogFile(previous);
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.getParent());
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void testNullErrorIsNoOp() throws Exception {
        Path previous = AppLog.logFile();
        Path file = Files.createTempFile("lide-log-null-", ".log");
        Files.deleteIfExists(file);
        try {
            AppLog.setLogFile(file);
            AppLog.exception("should not write", null);
            assertTrue(!Files.exists(file), "no file for null");
        } finally {
            AppLog.setLogFile(previous);
            Files.deleteIfExists(file);
        }
    }

}
