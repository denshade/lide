package lide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Text search helpers for find-in-file and find-in-files.
 */
public final class TextFinder {
    private TextFinder() {
    }

    /**
     * Finds the next occurrence at or after {@code fromInclusive}, wrapping to the start if needed.
     * Returns -1 when there is no match.
     */
    public static int findNext(String text, String query, int fromInclusive, boolean matchCase) {
        if (text == null || query == null || query.isEmpty()) {
            return -1;
        }
        String haystack = matchCase ? text : text.toLowerCase(Locale.ROOT);
        String needle = matchCase ? query : query.toLowerCase(Locale.ROOT);
        int from = Math.max(0, Math.min(fromInclusive, haystack.length()));
        int index = haystack.indexOf(needle, from);
        if (index < 0 && from > 0) {
            index = haystack.indexOf(needle);
        }
        return index;
    }

    /**
     * Finds the previous occurrence ending at or before {@code beforeExclusive}, wrapping to the end
     * if needed. Returns -1 when there is no match.
     */
    public static int findPrevious(String text, String query, int beforeExclusive, boolean matchCase) {
        if (text == null || query == null || query.isEmpty()) {
            return -1;
        }
        String haystack = matchCase ? text : text.toLowerCase(Locale.ROOT);
        String needle = matchCase ? query : query.toLowerCase(Locale.ROOT);
        int before = Math.max(0, Math.min(beforeExclusive, haystack.length()));
        int index = before == 0 ? -1 : haystack.lastIndexOf(needle, before - 1);
        if (index < 0) {
            index = haystack.lastIndexOf(needle);
        }
        return index;
    }

    public static int countMatches(String text, String query, boolean matchCase) {
        return findAll(text, query, matchCase).size();
    }

    /**
     * Returns every match offset in {@code text} without wrapping.
     */
    public static List<Integer> findAll(String text, String query, boolean matchCase) {
        List<Integer> found = new ArrayList<>();
        if (text == null || query == null || query.isEmpty()) {
            return found;
        }
        String haystack = matchCase ? text : text.toLowerCase(Locale.ROOT);
        String needle = matchCase ? query : query.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from <= haystack.length() - needle.length()) {
            int index = haystack.indexOf(needle, from);
            if (index < 0) {
                break;
            }
            found.add(index);
            from = index + Math.max(1, needle.length());
        }
        return found;
    }

    /**
     * Normalizes CR LF / CR line endings to LF so offsets match the editor document.
     */
    public static String normalizeNewlines(String text) {
        if (text == null || text.indexOf('\r') < 0) {
            return text == null ? "" : text;
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** 1-based line number of {@code offset} in LF-normalized text. */
    public static int lineNumber(String text, int offset) {
        if (text == null || offset <= 0) {
            return 1;
        }
        int line = 1;
        int end = Math.min(offset, text.length());
        for (int i = 0; i < end; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** Line containing {@code offset}, without the trailing newline. */
    public static String lineAt(String text, int offset) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int start = offset <= 0 ? 0 : text.lastIndexOf('\n', offset - 1) + 1;
        int end = text.indexOf('\n', Math.max(0, offset));
        if (end < 0) {
            end = text.length();
        }
        if (start < 0 || start > text.length() || end < start) {
            return "";
        }
        return text.substring(start, end);
    }
}
