package lide;

import java.util.Locale;

/**
 * Text search helpers for find-in-file.
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
        if (text == null || query == null || query.isEmpty()) {
            return 0;
        }
        String haystack = matchCase ? text : text.toLowerCase(Locale.ROOT);
        String needle = matchCase ? query : query.toLowerCase(Locale.ROOT);
        int count = 0;
        int from = 0;
        while (from <= haystack.length() - needle.length()) {
            int index = haystack.indexOf(needle, from);
            if (index < 0) {
                break;
            }
            count++;
            from = index + Math.max(1, needle.length());
        }
        return count;
    }
}
