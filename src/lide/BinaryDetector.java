package lide;

/**
 * Heuristics for detecting binary file content.
 */
public final class BinaryDetector {
    private static final int SAMPLE_SIZE = 8192;

    private BinaryDetector() {
    }

    /**
     * Returns true when the sample looks binary: contains a NUL byte, or a high
     * proportion of non-text control characters.
     */
    public static boolean isBinary(byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }
        int n = Math.min(data.length, SAMPLE_SIZE);
        int control = 0;
        for (int i = 0; i < n; i++) {
            int b = data[i] & 0xFF;
            if (b == 0) {
                return true;
            }
            if (b < 0x09 || (b > 0x0D && b < 0x20) || b == 0x7F) {
                control++;
            }
        }
        return control * 100 >= n * 10;
    }
}
