package lide;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Extension-based text vs binary classification, plus decoding for text files.
 */
public final class TextFiles {
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    /**
     * Common text formats that should always open in the text editor, even when
     * content heuristics would treat them as binary (UTF-16, Latin-1, and similar).
     */
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "java", "js", "mjs", "cjs", "ts", "tsx", "jsx", "go", "py",
            "xml", "html", "htm", "xhtml", "svg", "fxml",
            "md", "markdown", "txt", "text", "csv", "tsv",
            "json", "jsonc", "json5", "yml", "yaml", "toml", "ini", "cfg", "conf",
            "properties", "log", "env", "sql",
            "css", "scss", "sass", "less",
            "bat", "cmd", "ps1", "sh", "bash", "zsh", "fish",
            "gradle", "kts", "groovy",
            "c", "h", "cc", "cpp", "cxx", "hpp", "hxx", "cs",
            "rb", "php", "rs", "kt", "swift", "scala", "r", "lua", "pl", "pm",
            "vue", "svelte", "jsp",
            "rst", "adoc", "asciidoc", "tex", "diff", "patch",
            "graphql", "gql", "proto", "cmake", "mk", "mak",
            "gitignore", "gitattributes", "dockerignore", "editorconfig");

    private static final Set<String> TEXT_FILENAMES = Set.of(
            "makefile", "dockerfile", "readme", "license", "copying", "authors",
            "changelog", "gemfile", "rakefile", "procfile", "jenkinsfile",
            "vagrantfile", "brewfile", "cmakelists.txt");

    static final Set<String> BINARY_EXTENSIONS = Set.of(
            "class", "jar", "war", "zip", "7z", "gz", "tar", "rar",
            "png", "jpg", "jpeg", "gif", "ico", "bmp", "webp", "psd",
            "exe", "dll", "so", "dylib", "bin", "o", "obj", "pdb",
            "pdf", "woff", "woff2", "ttf", "eot", "mp3", "mp4");

    private TextFiles() {
    }

    public static boolean isKnownText(Path path) {
        String fileName = fileName(path);
        if (fileName == null) {
            return false;
        }
        if (TEXT_FILENAMES.contains(fileName)) {
            return true;
        }
        String ext = extension(fileName);
        return ext != null && TEXT_EXTENSIONS.contains(ext);
    }

    public static boolean isKnownBinary(Path path) {
        String fileName = fileName(path);
        if (fileName == null) {
            return false;
        }
        String ext = extension(fileName);
        return ext != null && BINARY_EXTENSIONS.contains(ext);
    }

    /**
     * Decodes bytes as text: UTF-8 (with BOM), UTF-16 when a BOM is present,
     * otherwise Windows-1252 when the content is not valid UTF-8.
     */
    public static String decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String text;
        if (startsWith(bytes, (byte) 0xFF, (byte) 0xFE)) {
            text = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        } else if (startsWith(bytes, (byte) 0xFE, (byte) 0xFF)) {
            text = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        } else if (startsWith(bytes, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF)) {
            text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        } else if (isValidUtf8(bytes)) {
            text = new String(bytes, StandardCharsets.UTF_8);
        } else {
            text = new String(bytes, WINDOWS_1252);
        }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            return text.substring(1);
        }
        return text;
    }

    private static boolean isValidUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException ex) {
            return false;
        }
    }

    private static boolean startsWith(byte[] bytes, byte... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String fileName(Path path) {
        if (path == null || path.getFileName() == null) {
            return null;
        }
        return path.getFileName().toString().toLowerCase(Locale.ROOT);
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1);
    }
}
