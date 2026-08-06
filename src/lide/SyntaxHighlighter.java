package lide;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Lightweight tokenizer-based syntax highlighter for the editor.
 */
public final class SyntaxHighlighter {
    private final SimpleAttributeSet defaultAttr = attr(IdeTheme.DEFAULT_TEXT, false);
    private final SimpleAttributeSet keywordAttr = attr(IdeTheme.KEYWORD, true);
    private final SimpleAttributeSet typeAttr = attr(IdeTheme.TYPE, false);
    private final SimpleAttributeSet stringAttr = attr(IdeTheme.STRING, false);
    private final SimpleAttributeSet commentAttr = attr(IdeTheme.COMMENT, false);
    private final SimpleAttributeSet numberAttr = attr(IdeTheme.NUMBER, false);
    private final SimpleAttributeSet annotationAttr = attr(IdeTheme.ANNOTATION, false);

    public void highlight(StyledDocument doc, Language language) {
        try {
            String text = doc.getText(0, doc.getLength());
            doc.setCharacterAttributes(0, doc.getLength(), defaultAttr, true);
            if (language == Language.PLAIN || text.isEmpty()) {
                return;
            }
            if (language == Language.XML) {
                highlightXml(doc, text);
                return;
            }
            highlightCode(doc, text, language);
        } catch (BadLocationException ignored) {
            // Document may have changed under us; ignore.
        }
    }

    private void highlightCode(StyledDocument doc, String text, Language language) {
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);

            if (language.cStyleComments() && c == '/' && i + 1 < n) {
                char next = text.charAt(i + 1);
                if (next == '/') {
                    int end = indexOfLineEnd(text, i + 2);
                    apply(doc, i, end - i, commentAttr);
                    i = end;
                    continue;
                }
                if (next == '*') {
                    int end = text.indexOf("*/", i + 2);
                    end = end < 0 ? n : end + 2;
                    apply(doc, i, end - i, commentAttr);
                    i = end;
                    continue;
                }
            }

            if (language.hashComments() && language == Language.PYTHON && c == '#') {
                int end = indexOfLineEnd(text, i + 1);
                apply(doc, i, end - i, commentAttr);
                i = end;
                continue;
            }

            if (c == '"' || c == '\'') {
                int end = scanString(text, i, c, language == Language.JAVA || language == Language.JAVASCRIPT);
                apply(doc, i, end - i, stringAttr);
                i = end;
                continue;
            }

            if (language == Language.JAVA && c == '@') {
                int end = scanIdentifier(text, i + 1);
                apply(doc, i, end - i, annotationAttr);
                i = end;
                continue;
            }

            if (Character.isDigit(c) && (i == 0 || !isIdentChar(text.charAt(i - 1)))) {
                int end = scanNumber(text, i);
                apply(doc, i, end - i, numberAttr);
                i = end;
                continue;
            }

            if (isIdentStart(c)) {
                int end = scanIdentifier(text, i);
                String word = text.substring(i, end);
                if (language.keywords().contains(word)) {
                    apply(doc, i, end - i, keywordAttr);
                } else if (language.types().contains(word)
                        || (language == Language.JAVA && looksLikeType(word))) {
                    apply(doc, i, end - i, typeAttr);
                }
                i = end;
                continue;
            }

            i++;
        }
    }

    private void highlightXml(StyledDocument doc, String text) {
        int i = 0;
        int n = text.length();
        while (i < n) {
            if (text.startsWith("<!--", i)) {
                int end = text.indexOf("-->", i + 4);
                end = end < 0 ? n : end + 3;
                apply(doc, i, end - i, commentAttr);
                i = end;
                continue;
            }
            if (text.charAt(i) == '<') {
                int end = text.indexOf('>', i + 1);
                end = end < 0 ? n : end + 1;
                apply(doc, i, end - i, keywordAttr);
                // Attribute strings inside the tag
                highlightXmlAttrs(doc, text, i, end);
                i = end;
                continue;
            }
            i++;
        }
    }

    private void highlightXmlAttrs(StyledDocument doc, String text, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                int sEnd = scanString(text, i, c, false);
                if (sEnd > end) {
                    sEnd = end;
                }
                apply(doc, i, sEnd - i, stringAttr);
                i = sEnd - 1;
            }
        }
    }

    private static boolean looksLikeType(String word) {
        return !word.isEmpty()
                && Character.isUpperCase(word.charAt(0))
                && word.chars().noneMatch(ch -> ch == '_');
    }

    private static int scanString(String text, int start, char quote, boolean allowEscapes) {
        int i = start + 1;
        int n = text.length();
        // Text blocks """ ... """
        if (quote == '"' && start + 2 < n
                && text.charAt(start + 1) == '"'
                && text.charAt(start + 2) == '"') {
            int end = text.indexOf("\"\"\"", start + 3);
            return end < 0 ? n : end + 3;
        }
        while (i < n) {
            char c = text.charAt(i);
            if (allowEscapes && c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            if (c == '\n') {
                return i;
            }
            i++;
        }
        return n;
    }

    private static int scanIdentifier(String text, int start) {
        int i = start;
        while (i < text.length() && isIdentChar(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int scanNumber(String text, int start) {
        int i = start;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isDigit(c) || c == '.' || c == '_'
                    || c == 'x' || c == 'X' || c == 'b' || c == 'B'
                    || c == 'l' || c == 'L' || c == 'f' || c == 'F'
                    || c == 'd' || c == 'D' || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F')) {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    private static int indexOfLineEnd(String text, int from) {
        int n = text.length();
        for (int i = from; i < n; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                return i;
            }
        }
        return n;
    }

    private static boolean isIdentStart(char c) {
        return Character.isJavaIdentifierStart(c);
    }

    private static boolean isIdentChar(char c) {
        return Character.isJavaIdentifierPart(c);
    }

    private void apply(StyledDocument doc, int offset, int length, SimpleAttributeSet attrs) {
        if (length > 0) {
            doc.setCharacterAttributes(offset, length, attrs, true);
        }
    }

    private static SimpleAttributeSet attr(Color color, boolean bold) {
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setForeground(set, color);
        StyleConstants.setBold(set, bold);
        StyleConstants.setFontFamily(set, IdeTheme.EDITOR_FONT.getFamily());
        StyleConstants.setFontSize(set, IdeTheme.EDITOR_FONT.getSize());
        return set;
    }

    /**
     * Collects line start offsets for the gutter.
     */
    public static List<Integer> lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        return starts;
    }
}
