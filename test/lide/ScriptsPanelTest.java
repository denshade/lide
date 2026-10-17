package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for script discovery and command building.
 */
public final class ScriptsPanelTest {

    @Test
    void testIsScript() throws Exception {
        assertTrue(ScriptFinder.isScript(Path.of("build.bat")), "bat");
        assertTrue(ScriptFinder.isScript(Path.of("run.ps1")), "ps1");
        assertTrue(ScriptFinder.isScript(Path.of("tool.py")), "py");
        assertTrue(!ScriptFinder.isScript(Path.of("Main.java")), "java not script");
    }

    @Test
    void testFindScriptsSkipsOutAndFindsBat() throws Exception {
        Path root = Files.createTempDirectory("lide-scripts");
        try {
            Files.writeString(root.resolve("build.bat"), "@echo off\n");
            Files.writeString(root.resolve("readme.txt"), "hi");
            Path out = root.resolve("out");
            Files.createDirectories(out);
            Files.writeString(out.resolve("ignore.bat"), "@echo off\n");
            Path nested = root.resolve("tools");
            Files.createDirectories(nested);
            Files.writeString(nested.resolve("clean.ps1"), "Write-Host hi");

            List<Path> scripts = ScriptFinder.findScripts(root);
            assertEquals(2, scripts.size(), "count");
            assertTrue(scripts.stream().anyMatch(p -> p.getFileName().toString().equals("build.bat")), "has build.bat");
            assertTrue(scripts.stream().anyMatch(p -> p.getFileName().toString().equals("clean.ps1")), "has clean.ps1");
            assertTrue(scripts.stream().noneMatch(p -> p.toString().contains("out")), "skips out");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testDisplayName() throws Exception {
        Path root = Path.of("C:/proj").toAbsolutePath().normalize();
        Path script = root.resolve("tools").resolve("run.bat");
        assertEquals("tools/run.bat", ScriptFinder.displayName(root, script), "relative");
    }

    @Test
    void testCommandForBat() throws Exception {
        Path script = Path.of("C:/proj/build.bat").toAbsolutePath().normalize();
        List<String> cmd = ScriptCommand.commandFor(script);
        assertTrue(cmd.get(0).toLowerCase().contains("cmd"), "cmd launcher");
        assertEquals("/c", cmd.get(1), "/c");
        assertTrue(cmd.get(2).endsWith("build.bat"), "script path");
    }

    @Test
    void testCommandForPs1() throws Exception {
        Path script = Path.of("C:/proj/go.ps1").toAbsolutePath().normalize();
        List<String> cmd = ScriptCommand.commandFor(script);
        assertTrue(cmd.get(0).toLowerCase().contains("powershell")
                || cmd.get(0).equalsIgnoreCase("pwsh"), "powershell");
        assertTrue(cmd.contains("-File"), "has -File");
    }

    @Test
    void testCommandForPy() throws Exception {
        Path script = Path.of("C:/proj/tool.py").toAbsolutePath().normalize();
        List<String> cmd = ScriptCommand.commandFor(script);
        assertTrue(cmd.get(0).equals("py") || cmd.get(0).equals("python3"), "python launcher");
        assertTrue(cmd.get(cmd.size() - 1).endsWith("tool.py"), "script last");
    }

    @Test
    void testPanelRefresh() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "SKIP panel refresh in headless");
        Path root = Files.createTempDirectory("lide-scripts-ui");
        try {
            Files.writeString(root.resolve("a.bat"), "@echo off\necho hi\n");
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                ScriptsPanel panel = new ScriptsPanel();
                panel.setProjectRoot(root);
                assertEquals(1, panel.getScriptCount(), "panel count");
            });
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testRunCommand() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "SKIP runCommand in headless");
        Path root = Files.createTempDirectory("lide-run-cmd");
        try {
            ScriptsPanel panel = new ScriptsPanel();
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                panel.setProjectRoot(root);
                List<String> cmd = ScriptCommand.isWindows()
                        ? List.of("cmd.exe", "/c", "echo hello-ladle")
                        : List.of("echo", "hello-ladle");
                panel.runCommand(cmd, root);
            });
            long deadline = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < deadline) {
                if (panel.getOutputText().contains("[exit code")) {
                    break;
                }
                Thread.sleep(50);
            }
            String[] holder = new String[1];
            javax.swing.SwingUtilities.invokeAndWait(() -> holder[0] = panel.getOutputText());
            String output = holder[0];
            assertTrue(!panel.isRunning(), "not running");
            assertTrue(output.contains("echo"), "shows command");
            assertTrue(output.contains("hello-ladle"), "shows hello");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testStopRunning() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "SKIP stopRunning in headless");
        Path root = Files.createTempDirectory("lide-stop-cmd");
        try {
            Path script = writeHangScript(root);
            ScriptsPanel panel = new ScriptsPanel();
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                panel.setProjectRoot(root);
                panel.runScript(script);
            });
            assertTrue(waitUntil(() -> panel.isRunning(), 5000), "started");
            javax.swing.SwingUtilities.invokeAndWait(panel::stopRunning);
            assertTrue(waitUntil(() -> !panel.isRunning(), 8000), "stopped");
            String[] holder = new String[1];
            javax.swing.SwingUtilities.invokeAndWait(() -> holder[0] = panel.getOutputText());
            assertTrue(holder[0].contains("[stopped]"), "stopped message");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testFloodKeepsEdtResponsive() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "SKIP flood EDT in headless");
        Path root = Files.createTempDirectory("lide-flood-cmd");
        try {
            Path script = writeFloodScript(root, 4000);
            ScriptsPanel panel = new ScriptsPanel();
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                panel.setProjectRoot(root);
                panel.runScript(script);
            });
            assertTrue(waitUntil(
                    () -> panel.getOutputText().contains("flood-line-"), 8000), "flood started");
            long start = System.currentTimeMillis();
            javax.swing.SwingUtilities.invokeAndWait(() -> {
            });
            long waited = System.currentTimeMillis() - start;
            assertTrue(waited < 1000, "EDT responsive in " + waited + "ms");
            assertTrue(waitUntil(() -> !panel.isRunning(), 15000), "flood finished");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testConsoleOutputIsCapped() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "SKIP console cap in headless");
        Path root = Files.createTempDirectory("lide-cap-cmd");
        try {
            Path script = writeFloodScript(root, 200);
            ScriptsPanel panel = new ScriptsPanel();
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                panel.maxConsoleChars = 1500;
                panel.setProjectRoot(root);
                panel.runScript(script);
            });
            assertTrue(waitUntil(() -> !panel.isRunning(), 15000), "cap finished");
            String[] holder = new String[1];
            javax.swing.SwingUtilities.invokeAndWait(() -> holder[0] = panel.getOutputText());
            assertTrue(holder[0].length() <= 1500, "capped length " + holder[0].length());
            assertTrue(holder[0].contains("flood-line-"), "kept recent output");
        } finally {
            deleteRecursive(root);
        }
    }

    private static Path writeHangScript(Path root) throws Exception {
        if (ScriptCommand.isWindows()) {
            Path script = root.resolve("hang.bat");
            Files.writeString(script, "@echo off\r\nping -n 40 127.0.0.1\r\n");
            return script;
        }
        Path script = root.resolve("hang.sh");
        Files.writeString(script, "#!/bin/sh\nsleep 40\n");
        return script;
    }

    private static Path writeFloodScript(Path root, int lines) throws Exception {
        if (ScriptCommand.isWindows()) {
            Path script = root.resolve("flood.bat");
            Files.writeString(script,
                    "@echo off\r\nfor /L %%i in (1,1," + lines + ") do echo flood-line-%%i\r\n");
            return script;
        }
        Path script = root.resolve("flood.sh");
        Files.writeString(script,
                "#!/bin/sh\ni=1\nwhile [ \"$i\" -le " + lines
                        + " ]; do echo flood-line-$i; i=$((i+1)); done\n");
        return script;
    }

    private static boolean waitUntil(Check check, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (check.ok()) {
                return true;
            }
            Thread.sleep(25);
        }
        return check.ok();
    }

    @FunctionalInterface
    private interface Check {
        boolean ok() throws Exception;
    }

    private static void deleteRecursive(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            List<Path> paths = walk.sorted((a, b) -> b.compareTo(a)).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

}
