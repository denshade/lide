package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Assumptions;
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

    @Test
    void testIdentifierAt() throws Exception {
        String text = "CodeEditor editor = new CodeEditor();";
        int offset = text.indexOf("CodeEditor");
        assertEquals("CodeEditor", ClassNavigator.identifierAt(text, offset + 3), "identifier");
        assertEquals("editor", ClassNavigator.identifierAt(text, text.indexOf("editor") + 2), "middle of word");
        assertNull(ClassNavigator.identifierAt(text, text.indexOf('=')), "non-ident");
    }

    @Test
    void testIsNavigableClassName() throws Exception {
        assertTrue(ClassNavigator.isNavigableClassName("CodeEditor", Language.JAVA), "Java type");
        assertTrue(ClassNavigator.isNavigableClassName("String", Language.JAVA), "known type");
        assertTrue(!ClassNavigator.isNavigableClassName("class", Language.JAVA), "not keyword");
        assertTrue(!ClassNavigator.isNavigableClassName("openFile", Language.JAVA), "lowercase method");
    }

    @Test
    void testParseImports() throws Exception {
        String src = """
                package lide;
                import java.util.List;
                import javax.swing.JFrame;
                import static java.util.Objects.requireNonNull;
                """;
        Map<String, String> imports = ClassNavigator.parseJavaImports(src);
        assertEquals("java.util.List", imports.get("List"), "List");
        assertEquals("javax.swing.JFrame", imports.get("JFrame"), "JFrame");
        assertEquals("java.util.Objects.requireNonNull", imports.get("requireNonNull"), "requireNonNull");
    }

    @Test
    void testFindDeclarationOffset() throws Exception {
        String src = """
                package lide;
                public final class CodeEditor {
                }
                """;
        int offset = ClassNavigator.findDeclarationOffset(src, "CodeEditor", Language.JAVA);
        assertEquals(src.indexOf("CodeEditor"), offset, "decl offset");
    }

    @Test
    void testResolveSamePackageFile() throws Exception {
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
            assertTrue(target.isPresent(), "resolved");
            assertEquals(editor.toAbsolutePath().normalize(), target.get().path(), "path");
            assertEquals(Files.readString(editor).indexOf("CodeEditor"), target.get().caretOffset(), "caret");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testResolveViaImport() throws Exception {
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
            assertTrue(target.isPresent(), "import resolve");
            assertEquals(helper.toAbsolutePath().normalize(), target.get().path(), "helper path");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testResolveWithCrlfSourceAndDeclaration() throws Exception {
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
            assertTrue(target.isPresent(), "crlf resolved");
            assertEquals(icon.toAbsolutePath().normalize(), target.get().path(), "crlf path");

            String iconDoc = iconSrc.replace("\r\n", "\n");
            assertEquals(iconDoc.indexOf("IconGenerator"), target.get().caretOffset(), "crlf caret on class name");
            assertEquals("IconGenerator", iconDoc.substring(
                            target.get().caretOffset(),
                            target.get().caretOffset() + "IconGenerator".length()), "caret text");
        } finally {
            deleteRecursive(root);
        }
    }

    /**
     * JTextPane.getText() rewrites newlines to platform CRLF while viewToModel offsets
     * stay in the LF document model — Ctrl+click must use document text.
     */
    @Test
    void testCtrlClickIconGeneratorWithCrlf() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testCtrlClickIconGeneratorWithCrlf);
            return;
        }
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
            assertTrue(editor != null, "editor open");

            String rewritten = editor.getText();
            String doc = editor.getDocumentText();
            assertTrue(!doc.contains("\r"), "document text is LF-only");
            assertTrue(rewritten.contains("\r"), "getText rewrites EOL");
            assertTrue(rewritten.length() > doc.length(), "getText longer than document");

            int docOffset = doc.indexOf("IconGenerator");
            assertTrue(docOffset >= 0, "IconGenerator in doc");

            // Same numeric offset against rewritten getText() is wrong (the old bug).
            String wrongWord = ClassNavigator.identifierAt(rewritten, docOffset);
            assertTrue(!"IconGenerator".equals(wrongWord), "getText+docOffset is not IconGenerator");

            Optional<ClassNavigator.Target> target = ClassNavigator.resolve(
                    root, app, doc, Language.JAVA, docOffset);
            assertTrue(target.isPresent(), "doc-offset resolve");
            assertEquals(icon.toAbsolutePath().normalize(), target.get().path(), "doc path");

            pane.navigateTo(target.get());
            CodeEditor active = pane.getActiveEditor();
            assertTrue(active != null, "navigated editor");
            assertEquals(icon.toAbsolutePath().normalize(), active.getFilePath(), "opened IconGenerator");
            String opened = active.getDocumentText();
            assertEquals("IconGenerator", opened.substring(
                            active.getCaretPosition(),
                            active.getCaretPosition() + "IconGenerator".length()), "caret on class name");
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

}
