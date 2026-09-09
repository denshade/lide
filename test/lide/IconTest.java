package lide;

import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
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
        testDialogIcon();
        if (!GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeAndWait(IconTest::testMainFrameHasIcons);
            SwingUtilities.invokeAndWait(IconTest::testAboutMenuItemUsesAppIcon);
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
            Path icns = dir.resolve("lide.icns");
            IconGenerator.writePng(png, 64);
            IconGenerator.writeIco(ico);
            IconGenerator.writeIcns(icns);
            assertTrue("png exists", Files.size(png) > 0);
            assertTrue("ico exists", Files.size(ico) > 100);
            byte[] header = Files.readAllBytes(ico);
            assertEqual("ico reserved", 0, header[0] | (header[1] << 8));
            assertEqual("ico type", 1, header[2] | (header[3] << 8));
            assertTrue("ico count", (header[4] | (header[5] << 8)) >= 1);
            assertTrue("icns exists", Files.size(icns) > 100);
            byte[] icnsHeader = Files.readAllBytes(icns);
            assertEqual("icns magic0", (int) 'i', icnsHeader[0] & 0xFF);
            assertEqual("icns magic1", (int) 'c', icnsHeader[1] & 0xFF);
            assertEqual("icns magic2", (int) 'n', icnsHeader[2] & 0xFF);
            assertEqual("icns magic3", (int) 's', icnsHeader[3] & 0xFF);
            int declared = ((icnsHeader[4] & 0xFF) << 24)
                    | ((icnsHeader[5] & 0xFF) << 16)
                    | ((icnsHeader[6] & 0xFF) << 8)
                    | (icnsHeader[7] & 0xFF);
            assertEqual("icns length", icnsHeader.length, declared);
        } finally {
            try (var walk = Files.walk(dir)) {
                walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ex) {
                        AppLog.exception("Could not delete " + path, ex);
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

    private static void testDialogIcon() {
        Icon icon = AppIcons.dialogIcon(MainFrame.ABOUT_ICON_SIZE);
        assertTrue("dialog icon present", icon != null);
        assertEqual("dialog icon width", MainFrame.ABOUT_ICON_SIZE, icon.getIconWidth());
        assertEqual("dialog icon height", MainFrame.ABOUT_ICON_SIZE, icon.getIconHeight());

        BufferedImage rendered = toImage(icon);
        Image appIcon = null;
        for (Image candidate : AppIcons.loadWindowIcons()) {
            if (candidate.getWidth(null) == MainFrame.ABOUT_ICON_SIZE) {
                appIcon = candidate;
                break;
            }
        }
        assertTrue("app icon at dialog size available", appIcon != null);
        if (appIcon == null) {
            return;
        }
        BufferedImage expected = toImage(new ImageIcon(appIcon));
        boolean identical = true;
        for (int y = 0; y < expected.getHeight() && identical; y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                if (expected.getRGB(x, y) != rendered.getRGB(x, y)) {
                    identical = false;
                    break;
                }
            }
        }
        assertTrue("dialog icon matches application icon", identical);
    }

    private static BufferedImage toImage(Icon icon) {
        BufferedImage image = new BufferedImage(
                icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            icon.paintIcon(null, g, 0, 0);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void testAboutMenuItemUsesAppIcon() {
        MainFrame frame = new MainFrame();
        try {
            JMenuItem about = findMenuItem(frame.getJMenuBar(), "About Lide");
            assertTrue("about menu item exists", about != null);
            Icon icon = MainFrame.aboutDialogIcon();
            assertTrue("about dialog icon present", icon != null);
            assertEqual("about dialog icon size", MainFrame.ABOUT_ICON_SIZE,
                    icon == null ? -1 : icon.getIconWidth());
        } finally {
            frame.dispose();
        }
    }

    private static JMenuItem findMenuItem(JMenuBar bar, String text) {
        if (bar == null) {
            return null;
        }
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu == null) {
                continue;
            }
            for (int j = 0; j < menu.getItemCount(); j++) {
                JMenuItem item = menu.getItem(j);
                if (item != null && text.equals(item.getText())) {
                    return item;
                }
            }
        }
        return null;
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
