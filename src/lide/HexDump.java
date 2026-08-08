package lide;

/**
 * Formats byte arrays as classic hex dumps for the binary viewer.
 */
public final class HexDump {
    public static final int DEFAULT_MAX_BYTES = 256 * 1024;

    private HexDump() {
    }

    public static String format(byte[] data) {
        return format(data, DEFAULT_MAX_BYTES);
    }

    public static String format(byte[] data, int maxBytes) {
        if (data == null || data.length == 0) {
            return "";
        }
        int length = Math.min(data.length, Math.max(0, maxBytes));
        StringBuilder out = new StringBuilder(length * 4);
        for (int offset = 0; offset < length; offset += 16) {
            out.append(String.format("%08X  ", offset));
            int lineEnd = Math.min(offset + 16, length);
            for (int i = 0; i < 16; i++) {
                if (offset + i < lineEnd) {
                    out.append(String.format("%02X ", data[offset + i] & 0xFF));
                } else {
                    out.append("   ");
                }
                if (i == 7) {
                    out.append(' ');
                }
            }
            out.append(" |");
            for (int i = offset; i < lineEnd; i++) {
                int b = data[i] & 0xFF;
                out.append(b >= 0x20 && b < 0x7F ? (char) b : '.');
            }
            out.append("|\n");
        }
        if (length < data.length) {
            out.append("\n… truncated after ")
                    .append(length)
                    .append(" of ")
                    .append(data.length)
                    .append(" bytes\n");
        }
        return out.toString();
    }
}
