package lide;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Supported languages for syntax highlighting.
 */
public enum Language {
    JAVA(
            Set.of(
                    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
                    "class", "const", "continue", "default", "do", "double", "else", "enum",
                    "extends", "final", "finally", "float", "for", "goto", "if", "implements",
                    "import", "instanceof", "int", "interface", "long", "native", "new", "package",
                    "private", "protected", "public", "return", "short", "static", "strictfp",
                    "super", "switch", "synchronized", "this", "throw", "throws", "transient",
                    "try", "void", "volatile", "while", "var", "record", "sealed", "permits",
                    "yield", "when", "true", "false", "null"
            ),
            Set.of(
                    "String", "Object", "Integer", "Long", "Double", "Float", "Boolean", "Character",
                    "Byte", "Short", "Void", "Class", "System", "Math", "List", "Map", "Set",
                    "Optional", "ArrayList", "HashMap", "HashSet", "Override", "Deprecated",
                    "SuppressWarnings", "FunctionalInterface"
            ),
            true,
            true
    ),
    JAVASCRIPT(
            Set.of(
                    "break", "case", "catch", "class", "const", "continue", "debugger", "default",
                    "delete", "do", "else", "export", "extends", "finally", "for", "function",
                    "if", "import", "in", "instanceof", "let", "new", "return", "super", "switch",
                    "this", "throw", "try", "typeof", "var", "void", "while", "with", "yield",
                    "async", "await", "of", "static", "true", "false", "null", "undefined",
                    "from", "as"
            ),
            Set.of(),
            true,
            true
    ),
    GO(
            Set.of(
                    "break", "case", "chan", "const", "continue", "default", "defer", "else",
                    "fallthrough", "for", "func", "go", "goto", "if", "import", "interface",
                    "map", "package", "range", "return", "select", "struct", "switch", "type",
                    "var", "true", "false", "nil", "iota"
            ),
            Set.of(
                    "any", "bool", "byte", "comparable", "complex64", "complex128", "error",
                    "float32", "float64", "int", "int8", "int16", "int32", "int64", "rune",
                    "string", "uint", "uint8", "uint16", "uint32", "uint64", "uintptr"
            ),
            true,
            false
    ),
    PYTHON(
            Set.of(
                    "False", "None", "True", "and", "as", "assert", "async", "await", "break",
                    "class", "continue", "def", "del", "elif", "else", "except", "finally", "for",
                    "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", "not",
                    "or", "pass", "raise", "return", "try", "while", "with", "yield", "match", "case"
            ),
            Set.of(),
            false,
            true
    ),
    XML(Set.of(), Set.of(), false, false),
    PLAIN(Set.of(), Set.of(), false, false);

    private final Set<String> keywords;
    private final Set<String> types;
    private final boolean cStyleComments;
    private final boolean hashComments;

    Language(Set<String> keywords, Set<String> types, boolean cStyleComments, boolean hashComments) {
        this.keywords = keywords;
        this.types = types;
        this.cStyleComments = cStyleComments;
        this.hashComments = hashComments;
    }

    public Set<String> keywords() {
        return keywords;
    }

    public Set<String> types() {
        return types;
    }

    public boolean cStyleComments() {
        return cStyleComments;
    }

    public boolean hashComments() {
        return hashComments;
    }

    public static Language fromPath(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return PLAIN;
        }
        String ext = name.substring(dot + 1);
        return switch (ext) {
            case "java" -> JAVA;
            case "js", "mjs", "cjs", "ts", "tsx", "jsx" -> JAVASCRIPT;
            case "go" -> GO;
            case "py" -> PYTHON;
            case "xml", "html", "htm", "xhtml", "svg", "fxml" -> XML;
            case "json", "jsonc", "md", "markdown", "txt", "text", "csv", "tsv",
                    "css", "scss", "sass", "less", "properties", "gradle", "kts",
                    "yml", "yaml", "toml", "ini", "cfg", "conf", "log", "env", "sql" -> PLAIN;
            default -> PLAIN;
        };
    }
}
