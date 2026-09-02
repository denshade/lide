package lide;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

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
        testResolveWithCrlfSourceAndDeclaration();
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: headless environment cannot run Ctrl+click UI tests");
        } else {
            SwingUtilities.invokeAndWait(ClassNavigatorTest::testCtrlClickIconGeneratorWithCrlf);
        }
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

    private static void testResolveWithCrlfSourceAndDeclaration() throws Exception {
        Path root = Files.createTempDirectory("lide-nav-crlf");
        try {
            Path pkg = root.resolve("src").resolve("lide");
            Files.createDirectories(pkg);
            // CRLF on disk like Windows checkouts; declaration offset must use LF model coords.
            String iconSrc = "package lide;\r\n"
                    + "\r\n"
                    + "import java.awt.Color;\r\n"
                    + "import java.awt.Font;\r\n"
                    + "\r\n"
                    + "public final class IconGenerator {\r\n"
                    + "    public static void render() {}\r\n"
                    + "}\r\n";
            Path icon = pkg.resolve("IconGenerator.java");
            Files.writeString(icon, iconSrc);
            String appSrc = "package lide;\r\n"
                    + "\r\n"
                    + "public class AppIcons {\r\n"
                    + "    void m() {\r\n"
                    + "        IconGenerator.render();\r\n"
                    + "    }\r\n"
                    + "}\r\n";
            Path app = pkg.resolve("AppIcons.java");
            Files.writeString(app, appSrc);

            // Editor document model strips CR; offsets from viewToModel use that model.
            String appDoc = appSrc.replace("\r\n", "\n");
            int offset = appDoc.indexOf("IconGenerator");
            Optional<ClassNavigator.Target> target = ClassNavigator.resolve(
                    root, app, appDoc, Language.JAVA, offset);
            assertTrue("crlf resolved", target.isPresent());
            assertEqual("crlf path", icon.toAbsolutePath().normalize(), target.get().path());

            String iconDoc = iconSrc.replace("\r\n", "\n");
            assertEqual(
                    "crlf caret on class name",
                    iconDoc.indexOf("IconGenerator"),
                    target.get().caretOffset());
            assertEqual(
                    "caret text",
                    "IconGenerator",
                    iconDoc.substring(
                            target.get().caretOffset(),
                            target.get().caretOffset() + "IconGenerator".length()));
        } finally {
            deleteRecursive(root);
        }
    }

    /**
     * JTextPane.getText() rewrites newlines to platform CRLF while viewToModel offsets
     * stay in the LF document model — Ctrl+click must use document text.
     */
    private static void testCtrlClickIconGeneratorWithCrlf() {
        Path created = null;
        JFrame frame = new JFrame("Ctrl+click CRLF test");
        try {
            created = Files.createTempDirectory("lide-ctrl-crlf");
            Path root = created;
            Path pkg = root.resolve("src").resolve("lide");
            Files.createDirectories(pkg);
            Path icon = pkg.resolve("IconGenerator.java");
            Path app = pkg.resolve("AppIcons.java");
            Files.writeString(
                    icon,
                    "package lide;\r\n\r\npublic final class IconGenerator {\r\n}\r\n");
            String appSrc = "package lide;\r\n\r\npublic class AppIcons {\r\n"
                    + "    void m() { IconGenerator.render(); }\r\n}\r\n";
            Files.writeString(app, appSrc);

            EditorTabPane pane = new EditorTabPane();
            pane.setProjectRootSupplier(() -> root);
            frame.add(pane);
            frame.setSize(900, 700);
            frame.setVisible(true);

            pane.openFile(app);
            CodeEditor editor = pane.getActiveEditor();
            assertTrue("editor open", editor != null);

            String rewritten = editor.getText();
            String doc = editor.getDocumentText();
            assertTrue("document text is LF-only", !doc.contains("\r"));
            assertTrue("getText rewrites EOL", rewritten.contains("\r"));
            assertTrue("getText longer than document", rewritten.length() > doc.length());

            int docOffset = doc.indexOf("IconGenerator");
            assertTrue("IconGenerator in doc", docOffset >= 0);

            // Same numeric offset against rewritten getText() is wrong (the old bug).
            String wrongWord = ClassNavigator.identifierAt(rewritten, docOffset);
            assertTrue(
                    "getText+docOffset is not IconGenerator",
                    !"IconGenerator".equals(wrongWord));

            Optional<ClassNavigator.Target> target = ClassNavigator.resolve(
                    root, app, doc, Language.JAVA, docOffset);
            assertTrue("doc-offset resolve", target.isPresent());
            assertEqual("doc path", icon.toAbsolutePath().normalize(), target.get().path());

            pane.navigateTo(target.get());
            CodeEditor active = pane.getActiveEditor();
            assertTrue("navigated editor", active != null);
            assertEqual(
                    "opened IconGenerator",
                    icon.toAbsolutePath().normalize(),
                    active.getFilePath());
            String opened = active.getDocumentText();
            assertEqual(
                    "caret on class name",
                    "IconGenerator",
                    opened.substring(
                            active.getCaretPosition(),
                            active.getCaretPosition() + "IconGenerator".length()));
        } catch (Exception ex) {
            failed++;
            System.err.println("FAIL ctrl-click crlf UI: " + ex);
            ex.printStackTrace(System.err);
        } finally {
            frame.dispose();
            if (created != null) {
                try {
                    deleteRecursive(created);
                } catch (Exception ex) {
                    AppLog.exception("Could not delete temp project " + created, ex);
                }
            }
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
