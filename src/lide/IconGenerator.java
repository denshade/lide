package lide;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Generates Lide application icons (PNG, ICO, and ICNS).
 */
public final class IconGenerator {
    private static final int[] ICO_SIZES = {16, 32, 48, 256};
    private static final int[] ICNS_SIZES = {16, 32, 64, 128, 256, 512};
    private static final byte[][] ICNS_TYPES = {
            {'i', 'c', 'p', '4'},
            {'i', 'c', 'p', '5'},
            {'i', 'c', 'p', '6'},
            {'i', 'c', '0', '7'},
            {'i', 'c', '0', '8'},
            {'i', 'c', '0', '9'}
    };

    private IconGenerator() {
    }

    public static BufferedImage render(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            float arc = size * 0.22f;
            g.setColor(new Color(0x2B2B2B));
            g.fill(new RoundRectangle2D.Float(0, 0, size, size, arc, arc));

            g.setColor(new Color(0x3592C4));
            float inset = size * 0.08f;
            g.setStroke(new BasicStroke(Math.max(1.5f, size * 0.06f)));
            g.draw(new RoundRectangle2D.Float(
                    inset, inset, size - inset * 2, size - inset * 2, arc * 0.8f, arc * 0.8f));

            // Accent bar suggesting an editor gutter / tab strip.
            int barWidth = Math.max(2, size / 8);
            g.fillRoundRect(
                    Math.round(size * 0.18f),
                    Math.round(size * 0.22f),
                    barWidth,
                    Math.round(size * 0.56f),
                    barWidth,
                    barWidth);

            int fontSize = Math.max(10, Math.round(size * 0.52f));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            FontMetrics fm = g.getFontMetrics();
            String letter = "L";
            int textX = Math.round(size * 0.38f);
            int textY = (size - fm.getHeight()) / 2 + fm.getAscent();
            g.setColor(new Color(0xA9B7C6));
            g.drawString(letter, textX, textY);
        } finally {
            g.dispose();
        }
        return image;
    }

    public static void writePng(Path path, int size) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        ImageIO.write(render(size), "png", path.toFile());
    }

    public static void writeIco(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<byte[]> pngs = new ArrayList<>();
        for (int size : ICO_SIZES) {
            BufferedImage image = render(size);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            pngs.add(baos.toByteArray());
        }
        try (OutputStream out = Files.newOutputStream(path)) {
            writeIcoFile(out, pngs, ICO_SIZES);
        }
    }

    /**
     * Writes a PNG-compressed ICNS (supported since macOS 10.8).
     */
    public static void writeIcns(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<byte[]> pngs = new ArrayList<>();
        for (int size : ICNS_SIZES) {
            BufferedImage image = render(size);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            pngs.add(baos.toByteArray());
        }
        try (OutputStream out = Files.newOutputStream(path)) {
            writeIcnsFile(out, pngs, ICNS_TYPES);
        }
    }

    /**
     * Writes a PNG-compressed ICO (supported since Windows Vista).
     */
    static void writeIcoFile(OutputStream out, List<byte[]> pngPayloads, int[] sizes)
            throws IOException {
        if (pngPayloads.size() != sizes.length) {
            throw new IllegalArgumentException("PNG count must match sizes");
        }
        int count = pngPayloads.size();
        // ICONDIR
        writeShort(out, 0);     // reserved
        writeShort(out, 1);     // type = icon
        writeShort(out, count); // count

        int offset = 6 + (16 * count);
        for (int i = 0; i < count; i++) {
            int size = sizes[i];
            byte[] png = pngPayloads.get(i);
            out.write(size >= 256 ? 0 : size); // width
            out.write(size >= 256 ? 0 : size); // height
            out.write(0); // color count
            out.write(0); // reserved
            writeShort(out, 1);  // planes
            writeShort(out, 32); // bit count
            writeInt(out, png.length);
            writeInt(out, offset);
            offset += png.length;
        }
        for (byte[] png : pngPayloads) {
            out.write(png);
        }
    }

    /**
     * Writes a PNG-compressed ICNS. Each type is a 4-byte OSType matching
     * {@code pngPayloads} in order.
     */
    static void writeIcnsFile(OutputStream out, List<byte[]> pngPayloads, byte[][] types)
            throws IOException {
        if (pngPayloads.size() != types.length) {
            throw new IllegalArgumentException("PNG count must match icon types");
        }
        int fileLength = 8;
        for (byte[] png : pngPayloads) {
            fileLength += 8 + png.length;
        }
        out.write(new byte[] {'i', 'c', 'n', 's'});
        writeIntBe(out, fileLength);
        for (int i = 0; i < pngPayloads.size(); i++) {
            byte[] type = types[i];
            if (type.length != 4) {
                throw new IllegalArgumentException("ICNS type must be 4 bytes");
            }
            byte[] png = pngPayloads.get(i);
            out.write(type);
            writeIntBe(out, 8 + png.length);
            out.write(png);
        }
    }

    private static void writeShort(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static void writeInt(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    private static void writeIntBe(OutputStream out, int value) throws IOException {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Path assets = root.resolve("assets");
        writePng(assets.resolve("lide.png"), 256);
        writePng(assets.resolve("lide-32.png"), 32);
        writeIco(assets.resolve("lide.ico"));
        writeIcns(assets.resolve("lide.icns"));
        // Classpath copies used by the running app.
        Path icons = root.resolve("src").resolve("lide").resolve("icons");
        writePng(icons.resolve("lide-16.png"), 16);
        writePng(icons.resolve("lide-32.png"), 32);
        writePng(icons.resolve("lide-64.png"), 64);
        writePng(icons.resolve("lide-128.png"), 128);
        writePng(icons.resolve("lide-256.png"), 256);
        System.out.println("Wrote icons under " + assets + " and " + icons);
    }
}
