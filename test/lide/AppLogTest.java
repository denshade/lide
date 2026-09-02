package lide;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for recording exceptions to the app log file.
 */
public final class AppLogTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testDefaultLogFile();
        testWritesExceptionToFile();
        testCreatesMissingParentDirectory();
        testNullErrorIsNoOp();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testDefaultLogFile() {
        Path expected = Path.of(System.getProperty("user.home"), ".lide", "lide.log");
        assertEqual("default log file", expected, AppLog.defaultLogFile());
    }

    private static void testWritesExceptionToFile() throws Exception {
        Path previous = AppLog.logFile();
        Path file = Files.createTempFile("lide-log-", ".log");
        Files.deleteIfExists(file);
        try {
            AppLog.setLogFile(file);
            AppLog.exception("Could not read folder", new IOException("permission denied"));
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue("contains context", text.contains("Could not read folder"));
            assertTrue("contains message", text.contains("permission denied"));
            assertTrue("contains stack", text.contains("java.io.IOException"));
        } finally {
            AppLog.setLogFile(previous);
            Files.deleteIfExists(file);
        }
    }

    private static void testCreatesMissingParentDirectory() throws Exception {
        Path previous = AppLog.logFile();
        Path dir = Files.createTempDirectory("lide-log-parent");
        Path file = dir.resolve("nested").resolve("lide.log");
        try {
            AppLog.setLogFile(file);
            AppLog.exception("save failed", new IOException("disk full"));
            assertTrue("log created", Files.isRegularFile(file));
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue("nested context", text.contains("save failed"));
        } finally {
            AppLog.setLogFile(previous);
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.getParent());
            Files.deleteIfExists(dir);
        }
    }

    private static void testNullErrorIsNoOp() throws Exception {
        Path previous = AppLog.logFile();
        Path file = Files.createTempFile("lide-log-null-", ".log");
        Files.deleteIfExists(file);
        try {
            AppLog.setLogFile(file);
            AppLog.exception("should not write", null);
            assertTrue("no file for null", !Files.exists(file));
        } finally {
            AppLog.setLogFile(previous);
            Files.deleteIfExists(file);
        }
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

    private AppLogTest() {
    }
}
