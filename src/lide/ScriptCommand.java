package lide;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds OS process commands for running project scripts.
 */
public final class ScriptCommand {
    private ScriptCommand() {
    }

    public static List<String> commandFor(Path script) {
        if (script == null) {
            throw new IllegalArgumentException("script is required");
        }
        Path name = script.getFileName();
        String fileName = name == null ? "" : name.toString().toLowerCase(Locale.ROOT);
        String absolute = script.toAbsolutePath().normalize().toString();
        List<String> command = new ArrayList<>();

        if (fileName.endsWith(".bat") || fileName.endsWith(".cmd")) {
            command.add(isWindows() ? "cmd.exe" : "cmd");
            command.add("/c");
            command.add(absolute);
            return command;
        }
        if (fileName.endsWith(".ps1")) {
            command.add(isWindows() ? "powershell.exe" : "pwsh");
            command.add("-NoProfile");
            command.add("-ExecutionPolicy");
            command.add("Bypass");
            command.add("-File");
            command.add(absolute);
            return command;
        }
        if (fileName.endsWith(".py")) {
            command.add(isWindows() ? "py" : "python3");
            if (isWindows()) {
                command.add("-3");
            }
            command.add(absolute);
            return command;
        }
        if (fileName.endsWith(".sh") || fileName.endsWith(".bash")) {
            command.add("bash");
            command.add(absolute);
            return command;
        }
        command.add(absolute);
        return command;
    }

    public static ProcessBuilder processBuilder(Path script, Path workingDirectory) {
        ProcessBuilder builder = new ProcessBuilder(commandFor(script));
        Path cwd = workingDirectory != null
                ? workingDirectory.toAbsolutePath().normalize()
                : script.toAbsolutePath().normalize().getParent();
        if (cwd != null) {
            builder.directory(cwd.toFile());
        }
        builder.redirectErrorStream(true);
        return builder;
    }

    static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return File.pathSeparatorChar == ';' || os.contains("win");
    }

    static boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("mac") || os.contains("darwin");
    }
}
