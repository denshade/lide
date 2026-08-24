package lide;

import java.awt.Color;
import java.nio.file.Path;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Tests for language detection and tokenizer-based syntax highlighting.
 */
public final class SyntaxHighlighterTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testLanguageFromPath();
        testGoKeywordsAndTypes();
        testGoCommentsAndStrings();
        testJavaScriptKeywords();
        testJavaScriptTemplateString();
        testJsxTags();
        testJsxDoesNotTreatComparisonAsTag();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testLanguageFromPath() {
        assertEqual(".go", Language.GO, Language.fromPath(Path.of("main.go")));
        assertEqual(".js", Language.JAVASCRIPT, Language.fromPath(Path.of("app.js")));
        assertEqual(".jsx", Language.JAVASCRIPT, Language.fromPath(Path.of("App.jsx")));
        assertEqual(".mjs", Language.JAVASCRIPT, Language.fromPath(Path.of("mod.mjs")));
        assertEqual(".tsx", Language.JAVASCRIPT, Language.fromPath(Path.of("App.tsx")));
    }

    private static void testGoKeywordsAndTypes() throws Exception {
        String src = "package main\nfunc Hello() int { return 1 }\n";
        StyledDocument doc = highlight(src, Language.GO);
        assertColor("package keyword", IdeTheme.KEYWORD, colorAt(doc, src.indexOf("package")));
        assertColor("func keyword", IdeTheme.KEYWORD, colorAt(doc, src.indexOf("func")));
        assertColor("return keyword", IdeTheme.KEYWORD, colorAt(doc, src.indexOf("return")));
        assertColor("int type", IdeTheme.TYPE, colorAt(doc, src.indexOf("int")));
        assertColor("Hello exported", IdeTheme.TYPE, colorAt(doc, src.indexOf("Hello")));
        assertColor("plain ident", IdeTheme.DEFAULT_TEXT, colorAt(doc, src.indexOf("main")));
    }

    private static void testGoCommentsAndStrings() throws Exception {
        String src = "s := `raw`\n// line\nx := \"hi\"\n";
        StyledDocument doc = highlight(src, Language.GO);
        assertColor("raw string", IdeTheme.STRING, colorAt(doc, src.indexOf("`raw`") + 1));
        assertColor("line comment", IdeTheme.COMMENT, colorAt(doc, src.indexOf("//")));
        assertColor("quoted string", IdeTheme.STRING, colorAt(doc, src.indexOf("\"hi\"") + 1));
    }

    private static void testJavaScriptKeywords() throws Exception {
        String src = "export function App() { const x = 1; return x; }\n";
        StyledDocument doc = highlight(src, Language.JAVASCRIPT);
        assertColor("export keyword", IdeTheme.KEYWORD, colorAt(doc, src.indexOf("export")));
        assertColor("function keyword", IdeTheme.KEYWORD, colorAt(doc, src.indexOf("function")));
        assertColor("const keyword", IdeTheme.KEYWORD, colorAt(doc, src.indexOf("const")));
        assertColor("return keyword", IdeTheme.KEYWORD, colorAt(doc, src.indexOf("return")));
        assertColor("number", IdeTheme.NUMBER, colorAt(doc, src.indexOf('1')));
    }

    private static void testJavaScriptTemplateString() throws Exception {
        String src = "const s = `hello\nworld`;\n";
        StyledDocument doc = highlight(src, Language.JAVASCRIPT);
        assertColor("template start", IdeTheme.STRING, colorAt(doc, src.indexOf('`')));
        assertColor("template body", IdeTheme.STRING, colorAt(doc, src.indexOf("world")));
    }

    private static void testJsxTags() throws Exception {
        String src = "return <div className=\"ok\">{count}</div>;\n";
        StyledDocument doc = highlight(src, Language.JAVASCRIPT);
        assertColor("open tag", IdeTheme.KEYWORD, colorAt(doc, src.indexOf("<div")));
        assertColor("attr string", IdeTheme.STRING, colorAt(doc, src.indexOf("ok")));
        assertColor("jsx expr", IdeTheme.DEFAULT_TEXT, colorAt(doc, src.indexOf("count")));
        assertColor("close tag", IdeTheme.KEYWORD, colorAt(doc, src.indexOf("</div>")));
    }

    private static void testJsxDoesNotTreatComparisonAsTag() throws Exception {
        String src = "if (a < b) { return a; }\n";
        StyledDocument doc = highlight(src, Language.JAVASCRIPT);
        assertColor("ident a stays default", IdeTheme.DEFAULT_TEXT, colorAt(doc, src.indexOf("a <")));
        assertColor("ident b stays default", IdeTheme.DEFAULT_TEXT, colorAt(doc, src.indexOf('b')));
        assertColor("if keyword", IdeTheme.KEYWORD, colorAt(doc, src.indexOf("if")));
    }

    private static StyledDocument highlight(String text, Language language) throws Exception {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        doc.insertString(0, text, null);
        new SyntaxHighlighter().highlight(doc, language);
        return doc;
    }

    private static Color colorAt(StyledDocument doc, int offset) {
        return StyleConstants.getForeground(doc.getCharacterElement(offset).getAttributes());
    }

    private static void assertColor(String label, Color expected, Color actual) {
        assertEqual(label, expected, actual);
    }

    private static void assertEqual(String label, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
            return;
        }
        failed++;
        System.err.println("FAIL " + label + ": expected " + expected + " but was " + actual);
    }

    private SyntaxHighlighterTest() {
    }
}
