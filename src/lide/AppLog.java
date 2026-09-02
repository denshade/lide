package lide;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Appends recovered failures to {@code ~/.lide/lide.log} so they are not swallowed.
 */
public final class AppLog {
    private static final Object LOCK = new Object();
    private static Path logFile = defaultLogFile();

    private AppLog() {
    }

    public static Path defaultLogFile() {
        return Path.of(System.getProperty("user.home"), ".lide", "lide.log");
    }

    static Path logFile() {
        synchronized (LOCK) {
            return logFile;
        }
    }

    static void setLogFile(Path path) {
        synchronized (LOCK) {
            logFile = path != null ? path : defaultLogFile();
        }
    }

    public static void exception(String context, Throwable error) {
        if (error == null) {
            return;
        }
        String heading = context == null || context.isBlank() ? error.toString() : context;
        StringWriter buffer = new StringWriter();
        buffer.write(Instant.now().toString());
        buffer.write(" ");
        buffer.write(heading);
        buffer.write(System.lineSeparator());
        error.printStackTrace(new PrintWriter(buffer, true));
        buffer.write(System.lineSeparator());
        String text = buffer.toString();

        Path file;
        synchronized (LOCK) {
            file = logFile;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    file,
                    text,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException writeFailed) {
            System.err.print(text);
            writeFailed.printStackTrace(System.err);
        }
    }
}
