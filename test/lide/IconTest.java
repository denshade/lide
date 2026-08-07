package lide;

import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * Tests for icon generation and loading.
 */
public final class IconTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testRenderSizes();
        testWriteIcoAndPng();
        testLoadWindowIcons();
        if (!GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeAndWait(IconTest::testMainFrameHasIcons);
        }
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testRenderSizes() {
        var image = IconGenerator.render(32);
        assertEqual("width", 32, image.getWidth());
        assertEqual("height", 32, image.getHeight());
        assertTrue("has pixels", image.getRGB(16, 16) != 0);
    }

    private static void testWriteIcoAndPng() throws Exception {
        Path dir = Files.createTempDirectory("lide-icons");
        try {
            Path png = dir.resolve("lide.png");
            Path ico = dir.resolve("lide.ico");
            IconGenerator.writePng(png, 64);
            IconGenerator.writeIco(ico);
            assertTrue("png exists", Files.size(png) > 0);
            assertTrue("ico exists", Files.size(ico) > 100);
            byte[] header = Files.readAllBytes(ico);
            assertEqual("ico reserved", 0, header[0] | (header[1] << 8));
            assertEqual("ico type", 1, header[2] | (header[3] << 8));
            assertTrue("ico count", (header[4] | (header[5] << 8)) >= 1);
        } finally {
            try (var walk = Files.walk(dir)) {
                walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    private static void testLoadWindowIcons() {
        List<Image> icons = AppIcons.loadWindowIcons();
        assertTrue("icons loaded", !icons.isEmpty());
        assertTrue("first icon sized", icons.get(0).getWidth(null) > 0);
    }

    private static void testMainFrameHasIcons() {
        MainFrame frame = new MainFrame();
        try {
            List<Image> icons = frame.getIconImages();
            assertTrue("frame icons", icons != null && !icons.isEmpty());
        } finally {
            frame.dispose();
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

    private IconTest() {
    }
}
