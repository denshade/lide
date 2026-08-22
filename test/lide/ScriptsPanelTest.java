package lide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for script discovery and command building.
 */
public final class ScriptsPanelTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testIsScript();
        testFindScriptsSkipsOutAndFindsBat();
        testDisplayName();
        testCommandForBat();
        testCommandForPs1();
        testCommandForPy();
        testPanelRefresh();
        testRunCommand();
        testStopRunning();
        testFloodKeepsEdtResponsive();
        testConsoleOutputIsCapped();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testIsScript() {
        assertTrue("bat", ScriptFinder.isScript(Path.of("build.bat")));
        assertTrue("ps1", ScriptFinder.isScript(Path.of("run.ps1")));
        assertTrue("py", ScriptFinder.isScript(Path.of("tool.py")));
        assertTrue("java not script", !ScriptFinder.isScript(Path.of("Main.java")));
    }

    private static void testFindScriptsSkipsOutAndFindsBat() throws Exception {
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
            assertEqual("count", 2, scripts.size());
            assertTrue("has build.bat",
                    scripts.stream().anyMatch(p -> p.getFileName().toString().equals("build.bat")));
            assertTrue("has clean.ps1",
                    scripts.stream().anyMatch(p -> p.getFileName().toString().equals("clean.ps1")));
            assertTrue("skips out",
                    scripts.stream().noneMatch(p -> p.toString().contains("out")));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testDisplayName() {
        Path root = Path.of("C:/proj").toAbsolutePath().normalize();
        Path script = root.resolve("tools").resolve("run.bat");
        assertEqual("relative", "tools/run.bat", ScriptFinder.displayName(root, script));
    }

    private static void testCommandForBat() {
        Path script = Path.of("C:/proj/build.bat").toAbsolutePath().normalize();
        List<String> cmd = ScriptCommand.commandFor(script);
        assertTrue("cmd launcher", cmd.get(0).toLowerCase().contains("cmd"));
        assertEqual("/c", "/c", cmd.get(1));
        assertTrue("script path", cmd.get(2).endsWith("build.bat"));
    }

    private static void testCommandForPs1() {
        Path script = Path.of("C:/proj/go.ps1").toAbsolutePath().normalize();
        List<String> cmd = ScriptCommand.commandFor(script);
        assertTrue("powershell", cmd.get(0).toLowerCase().contains("powershell")
                || cmd.get(0).equalsIgnoreCase("pwsh"));
        assertTrue("has -File", cmd.contains("-File"));
    }

    private static void testCommandForPy() {
        Path script = Path.of("C:/proj/tool.py").toAbsolutePath().normalize();
        List<String> cmd = ScriptCommand.commandFor(script);
        assertTrue("python launcher", cmd.get(0).equals("py") || cmd.get(0).equals("python3"));
        assertTrue("script last", cmd.get(cmd.size() - 1).endsWith("tool.py"));
    }

    private static void testPanelRefresh() throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP panel refresh in headless");
            return;
        }
        Path root = Files.createTempDirectory("lide-scripts-ui");
        try {
            Files.writeString(root.resolve("a.bat"), "@echo off\necho hi\n");
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                ScriptsPanel panel = new ScriptsPanel();
                panel.setProjectRoot(root);
                assertEqual("panel count", 1, panel.getScriptCount());
            });
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testRunCommand() throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP runCommand in headless");
            return;
        }
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
            assertTrue("not running", !panel.isRunning());
            assertTrue("shows command", output.contains("echo"));
            assertTrue("shows hello", output.contains("hello-ladle"));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testStopRunning() throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP stopRunning in headless");
            return;
        }
        Path root = Files.createTempDirectory("lide-stop-cmd");
        try {
            Path script = writeHangScript(root);
            ScriptsPanel panel = new ScriptsPanel();
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                panel.setProjectRoot(root);
                panel.runScript(script);
            });
            assertTrue("started", waitUntil(() -> panel.isRunning(), 5000));
            javax.swing.SwingUtilities.invokeAndWait(panel::stopRunning);
            assertTrue("stopped", waitUntil(() -> !panel.isRunning(), 8000));
            String[] holder = new String[1];
            javax.swing.SwingUtilities.invokeAndWait(() -> holder[0] = panel.getOutputText());
            assertTrue("stopped message", holder[0].contains("[stopped]"));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testFloodKeepsEdtResponsive() throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP flood EDT in headless");
            return;
        }
        Path root = Files.createTempDirectory("lide-flood-cmd");
        try {
            Path script = writeFloodScript(root, 4000);
            ScriptsPanel panel = new ScriptsPanel();
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                panel.setProjectRoot(root);
                panel.runScript(script);
            });
            assertTrue("flood started", waitUntil(
                    () -> panel.getOutputText().contains("flood-line-"), 8000));
            long start = System.currentTimeMillis();
            javax.swing.SwingUtilities.invokeAndWait(() -> {
            });
            long waited = System.currentTimeMillis() - start;
            assertTrue("EDT responsive in " + waited + "ms", waited < 1000);
            assertTrue("flood finished", waitUntil(() -> !panel.isRunning(), 15000));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testConsoleOutputIsCapped() throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP console cap in headless");
            return;
        }
        Path root = Files.createTempDirectory("lide-cap-cmd");
        try {
            Path script = writeFloodScript(root, 200);
            ScriptsPanel panel = new ScriptsPanel();
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                panel.maxConsoleChars = 1500;
                panel.setProjectRoot(root);
                panel.runScript(script);
            });
            assertTrue("cap finished", waitUntil(() -> !panel.isRunning(), 15000));
            String[] holder = new String[1];
            javax.swing.SwingUtilities.invokeAndWait(() -> holder[0] = panel.getOutputText());
            assertTrue("capped length " + holder[0].length(), holder[0].length() <= 1500);
            assertTrue("kept recent output", holder[0].contains("flood-line-"));
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

    private ScriptsPanelTest() {
    }
}
