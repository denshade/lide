package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.awt.Color;
import java.nio.file.Path;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Tests for language detection and tokenizer-based syntax highlighting.
 */
public final class SyntaxHighlighterTest {

    @Test
    void testLanguageFromPath() throws Exception {
        assertEquals(Language.GO, Language.fromPath(Path.of("main.go")), ".go");
        assertEquals(Language.JAVASCRIPT, Language.fromPath(Path.of("app.js")), ".js");
        assertEquals(Language.JAVASCRIPT, Language.fromPath(Path.of("App.jsx")), ".jsx");
        assertEquals(Language.JAVASCRIPT, Language.fromPath(Path.of("mod.mjs")), ".mjs");
        assertEquals(Language.JAVASCRIPT, Language.fromPath(Path.of("App.tsx")), ".tsx");
        assertEquals(Language.PLAIN, Language.fromPath(Path.of("README.md")), ".md");
        assertEquals(Language.PLAIN, Language.fromPath(Path.of("notes.txt")), ".txt");
        assertEquals(Language.PLAIN, Language.fromPath(Path.of("data.csv")), ".csv");
        assertEquals(Language.PLAIN, Language.fromPath(Path.of("config.yml")), ".yml");
    }

    @Test
    void testGoKeywordsAndTypes() throws Exception {
        String src = "package main\nfunc Hello() int { return 1 }\n";
        StyledDocument doc = highlight(src, Language.GO);
        assertEquals(IdeTheme.KEYWORD, colorAt(doc, src.indexOf("package")), "package keyword");
        assertEquals(IdeTheme.KEYWORD, colorAt(doc, src.indexOf("func")), "func keyword");
        assertEquals(IdeTheme.KEYWORD, colorAt(doc, src.indexOf("return")), "return keyword");
        assertEquals(IdeTheme.TYPE, colorAt(doc, src.indexOf("int")), "int type");
        assertEquals(IdeTheme.TYPE, colorAt(doc, src.indexOf("Hello")), "Hello exported");
        assertEquals(IdeTheme.DEFAULT_TEXT, colorAt(doc, src.indexOf("main")), "plain ident");
    }

    @Test
    void testGoCommentsAndStrings() throws Exception {
        String src = "s := `raw`\n// line\nx := \"hi\"\n";
        StyledDocument doc = highlight(src, Language.GO);
        assertEquals(IdeTheme.STRING, colorAt(doc, src.indexOf("`raw`") + 1), "raw string");
        assertEquals(IdeTheme.COMMENT, colorAt(doc, src.indexOf("//")), "line comment");
        assertEquals(IdeTheme.STRING, colorAt(doc, src.indexOf("\"hi\"") + 1), "quoted string");
    }

    @Test
    void testJavaScriptKeywords() throws Exception {
        String src = "export function App() { const x = 1; return x; }\n";
        StyledDocument doc = highlight(src, Language.JAVASCRIPT);
        assertEquals(IdeTheme.KEYWORD, colorAt(doc, src.indexOf("export")), "export keyword");
        assertEquals(IdeTheme.KEYWORD, colorAt(doc, src.indexOf("function")), "function keyword");
        assertEquals(IdeTheme.KEYWORD, colorAt(doc, src.indexOf("const")), "const keyword");
        assertEquals(IdeTheme.KEYWORD, colorAt(doc, src.indexOf("return")), "return keyword");
        assertEquals(IdeTheme.NUMBER, colorAt(doc, src.indexOf('1')), "number");
    }

    @Test
    void testJavaScriptTemplateString() throws Exception {
        String src = "const s = `hello\nworld`;\n";
        StyledDocument doc = highlight(src, Language.JAVASCRIPT);
        assertEquals(IdeTheme.STRING, colorAt(doc, src.indexOf('`')), "template start");
        assertEquals(IdeTheme.STRING, colorAt(doc, src.indexOf("world")), "template body");
    }

    @Test
    void testJsxTags() throws Exception {
        String src = "return <div className=\"ok\">{count}</div>;\n";
        StyledDocument doc = highlight(src, Language.JAVASCRIPT);
        assertEquals(IdeTheme.KEYWORD, colorAt(doc, src.indexOf("<div")), "open tag");
        assertEquals(IdeTheme.STRING, colorAt(doc, src.indexOf("ok")), "attr string");
        assertEquals(IdeTheme.DEFAULT_TEXT, colorAt(doc, src.indexOf("count")), "jsx expr");
        assertEquals(IdeTheme.KEYWORD, colorAt(doc, src.indexOf("</div>")), "close tag");
    }

    @Test
    void testJsxDoesNotTreatComparisonAsTag() throws Exception {
        String src = "if (a < b) { return a; }\n";
        StyledDocument doc = highlight(src, Language.JAVASCRIPT);
        assertEquals(IdeTheme.DEFAULT_TEXT, colorAt(doc, src.indexOf("a <")), "ident a stays default");
        assertEquals(IdeTheme.DEFAULT_TEXT, colorAt(doc, src.indexOf('b')), "ident b stays default");
        assertEquals(IdeTheme.KEYWORD, colorAt(doc, src.indexOf("if")), "if keyword");
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

}
