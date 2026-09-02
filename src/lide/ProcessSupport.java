package lide;

import java.io.IOException;
import java.util.List;

/**
 * Helpers for child processes started by the IDE.
 */
final class ProcessSupport {
    private ProcessSupport() {
    }

    /**
     * Forcibly stops {@code process} and every descendant, so tools that spawn
     * compilers or test JVMs (Ladle, Maven, Gradle) do not keep running after Stop.
     */
    static void destroyTree(Process process) {
        if (process == null) {
            return;
        }
        try {
            ProcessHandle handle = process.toHandle();
            List<ProcessHandle> descendants = handle.descendants().toList();
            for (ProcessHandle child : descendants) {
                child.destroyForcibly();
            }
            handle.destroyForcibly();
        } catch (IllegalStateException ex) {
            AppLog.exception("Could not destroy process tree; process not started or already reaped", ex);
        }
        process.destroyForcibly();
    }

    /**
     * Best-effort: drop the child below the IDE's scheduling class so a heavy
     * compile/test does not starve Swing. Failures are logged.
     */
    static void lowerPriority(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        long pid = process.pid();
        List<String> command = ScriptCommand.isWindows()
                ? List.of(
                        "powershell.exe",
                        "-NoProfile",
                        "-NonInteractive",
                        "-WindowStyle",
                        "Hidden",
                        "-Command",
                        "(Get-Process -Id " + pid + ").PriorityClass = 'BelowNormal'")
                : List.of("renice", "-n", "10", "-p", Long.toString(pid));
        try {
            new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException ex) {
            AppLog.exception("Could not lower process priority for pid " + pid, ex);
        }
    }
}
