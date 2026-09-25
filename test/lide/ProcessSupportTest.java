package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tests for destroying child process trees.
 */
public final class ProcessSupportTest {

    @Test
    void testDestroyTreeStopsProcess() throws Exception {
        Process process = startSleeper();
        try {
            assertTrue(process.isAlive(), "started");
            ProcessSupport.destroyTree(process);
            boolean ended = process.waitFor(8, TimeUnit.SECONDS);
            assertTrue(ended, "waited");
            assertTrue(!process.isAlive(), "dead");
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void testDestroyTreeStopsDescendants() throws Exception {
        Process process = startNestedSleeper();
        try {
            Thread.sleep(400);
            assertTrue(process.isAlive(), "parent started");
            List<ProcessHandle> descendants = process.toHandle().descendants().toList();
            assertTrue(!descendants.isEmpty(), "has descendant");
            ProcessSupport.destroyTree(process);
            boolean ended = process.waitFor(8, TimeUnit.SECONDS);
            assertTrue(ended, "parent waited");
            assertTrue(!process.isAlive(), "parent dead");
            for (ProcessHandle child : descendants) {
                child.onExit().orTimeout(8, TimeUnit.SECONDS).join();
                assertTrue(!child.isAlive(), "child dead " + child.pid());
            }
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void testNormalizeNewlines() throws Exception {
        assertEquals("a\nb\n", ScriptsPanel.normalizeNewlines("a\r\nb\r\n"), "crlf");
        assertEquals("a\nb", ScriptsPanel.normalizeNewlines("a\rb"), "cr");
        assertEquals("a\nb", ScriptsPanel.normalizeNewlines("a\nb"), "lf");
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

}
