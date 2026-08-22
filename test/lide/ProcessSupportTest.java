package lide;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tests for destroying child process trees.
 */
public final class ProcessSupportTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testDestroyTreeStopsProcess();
        testDestroyTreeStopsDescendants();
        testNormalizeNewlines();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testDestroyTreeStopsProcess() throws Exception {
        Process process = startSleeper();
        try {
            assertTrue("started", process.isAlive());
            ProcessSupport.destroyTree(process);
            boolean ended = process.waitFor(8, TimeUnit.SECONDS);
            assertTrue("waited", ended);
            assertTrue("dead", !process.isAlive());
        } finally {
            process.destroyForcibly();
        }
    }

    private static void testDestroyTreeStopsDescendants() throws Exception {
        Process process = startNestedSleeper();
        try {
            Thread.sleep(400);
            assertTrue("parent started", process.isAlive());
            List<ProcessHandle> descendants = process.toHandle().descendants().toList();
            assertTrue("has descendant", !descendants.isEmpty());
            ProcessSupport.destroyTree(process);
            boolean ended = process.waitFor(8, TimeUnit.SECONDS);
            assertTrue("parent waited", ended);
            assertTrue("parent dead", !process.isAlive());
            for (ProcessHandle child : descendants) {
                child.onExit().orTimeout(8, TimeUnit.SECONDS).join();
                assertTrue("child dead " + child.pid(), !child.isAlive());
            }
        } finally {
            process.destroyForcibly();
        }
    }

    private static void testNormalizeNewlines() {
        assertEqual("crlf", "a\nb\n", ScriptsPanel.normalizeNewlines("a\r\nb\r\n"));
        assertEqual("cr", "a\nb", ScriptsPanel.normalizeNewlines("a\rb"));
        assertEqual("lf", "a\nb", ScriptsPanel.normalizeNewlines("a\nb"));
    }

    private static Process startSleeper() throws Exception {
        if (ScriptCommand.isWindows()) {
            return new ProcessBuilder("ping", "-n", "25", "127.0.0.1")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }
        return new ProcessBuilder("sleep", "25")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private static Process startNestedSleeper() throws Exception {
        if (ScriptCommand.isWindows()) {
            return new ProcessBuilder("cmd.exe", "/c", "ping -n 25 127.0.0.1")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }
        return new ProcessBuilder("sh", "-c", "sleep 25")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
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

    private ProcessSupportTest() {
    }
}
