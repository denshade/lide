package lide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tests for Ctrl+click class navigation resolution.
 */
public final class ClassNavigatorTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testIdentifierAt();
        testIsNavigableClassName();
        testParseImports();
        testFindDeclarationOffset();
        testResolveSamePackageFile();
        testResolveViaImport();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testIdentifierAt() {
        String text = "CodeEditor editor = new CodeEditor();";
        int offset = text.indexOf("CodeEditor");
        assertEqual("identifier", "CodeEditor", ClassNavigator.identifierAt(text, offset + 3));
        assertEqual("middle of word", "editor", ClassNavigator.identifierAt(text, text.indexOf("editor") + 2));
        assertNull("non-ident", ClassNavigator.identifierAt(text, text.indexOf('=')));
    }

    private static void testIsNavigableClassName() {
        assertTrue("Java type", ClassNavigator.isNavigableClassName("CodeEditor", Language.JAVA));
        assertTrue("known type", ClassNavigator.isNavigableClassName("String", Language.JAVA));
        assertTrue("not keyword", !ClassNavigator.isNavigableClassName("class", Language.JAVA));
        assertTrue("lowercase method", !ClassNavigator.isNavigableClassName("openFile", Language.JAVA));
    }

    private static void testParseImports() {
        String src = """
                package lide;
                import java.util.List;
                import javax.swing.JFrame;
                import static java.util.Objects.requireNonNull;
                """;
        Map<String, String> imports = ClassNavigator.parseJavaImports(src);
        assertEqual("List", "java.util.List", imports.get("List"));
        assertEqual("JFrame", "javax.swing.JFrame", imports.get("JFrame"));
        assertEqual("requireNonNull", "java.util.Objects.requireNonNull",
                imports.get("requireNonNull"));
    }

    private static void testFindDeclarationOffset() {
        String src = """
                package lide;
                public final class CodeEditor {
                }
                """;
        int offset = ClassNavigator.findDeclarationOffset(src, "CodeEditor", Language.JAVA);
        assertEqual("decl offset", src.indexOf("CodeEditor"), offset);
    }

    private static void testResolveSamePackageFile() throws Exception {
        Path root = Files.createTempDirectory("lide-nav");
        try {
            Path pkg = root.resolve("src").resolve("lide");
            Files.createDirectories(pkg);
            Path editor = pkg.resolve("CodeEditor.java");
            Path main = pkg.resolve("MainFrame.java");
            Files.writeString(editor, "package lide;\npublic class CodeEditor {}\n");
            String mainSrc = """
                    package lide;
                    public class MainFrame {
                        CodeEditor editors;
                    }
                    """;
            Files.writeString(main, mainSrc);
            int offset = mainSrc.indexOf("CodeEditor");
            Optional<ClassNavigator.Target> target = ClassNavigator.resolve(
                    root, main, mainSrc, Language.JAVA, offset);
            assertTrue("resolved", target.isPresent());
            assertEqual("path", editor.toAbsolutePath().normalize(), target.get().path());
            assertEqual("caret", Files.readString(editor).indexOf("CodeEditor"),
                    target.get().caretOffset());
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testResolveViaImport() throws Exception {
        Path root = Files.createTempDirectory("lide-nav-imp");
        try {
            Path pkg = root.resolve("src").resolve("other");
            Files.createDirectories(pkg);
            Path helper = pkg.resolve("Helper.java");
            Files.writeString(helper, "package other;\npublic class Helper {}\n");
            Path current = root.resolve("App.java");
            String src = """
                    import other.Helper;
                    class App {
                        Helper h;
                    }
                    """;
            Files.writeString(current, src);
            Optional<ClassNavigator.Target> target = ClassNavigator.resolve(
                    root, current, src, Language.JAVA, src.indexOf("Helper h"));
            assertTrue("import resolve", target.isPresent());
            assertEqual("helper path", helper.toAbsolutePath().normalize(), target.get().path());
        } finally {
            deleteRecursive(root);
        }
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

    private static void assertNull(String label, Object actual) {
        assertEqual(label, null, actual);
    }

    private ClassNavigatorTest() {
    }
}
