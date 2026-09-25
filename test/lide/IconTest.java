package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
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

    @Test
    void testRenderSizes() throws Exception {
        var image = IconGenerator.render(32);
        assertEquals(32, image.getWidth(), "width");
        assertEquals(32, image.getHeight(), "height");
        assertTrue(image.getRGB(16, 16) != 0, "has pixels");
    }

    @Test
    void testWriteIcoAndPng() throws Exception {
        Path dir = Files.createTempDirectory("lide-icons");
        try {
            Path png = dir.resolve("lide.png");
            Path ico = dir.resolve("lide.ico");
            Path icns = dir.resolve("lide.icns");
            IconGenerator.writePng(png, 64);
            IconGenerator.writeIco(ico);
            IconGenerator.writeIcns(icns);
            assertTrue(Files.size(png) > 0, "png exists");
            assertTrue(Files.size(ico) > 100, "ico exists");
            byte[] header = Files.readAllBytes(ico);
            assertEquals(0, header[0] | (header[1] << 8), "ico reserved");
            assertEquals(1, header[2] | (header[3] << 8), "ico type");
            assertTrue((header[4] | (header[5] << 8)) >= 1, "ico count");
            assertTrue(Files.size(icns) > 100, "icns exists");
            byte[] icnsHeader = Files.readAllBytes(icns);
            assertEquals((int) 'i', icnsHeader[0] & 0xFF, "icns magic0");
            assertEquals((int) 'c', icnsHeader[1] & 0xFF, "icns magic1");
            assertEquals((int) 'n', icnsHeader[2] & 0xFF, "icns magic2");
            assertEquals((int) 's', icnsHeader[3] & 0xFF, "icns magic3");
            int declared = ((icnsHeader[4] & 0xFF) << 24)
                    | ((icnsHeader[5] & 0xFF) << 16)
                    | ((icnsHeader[6] & 0xFF) << 8)
                    | (icnsHeader[7] & 0xFF);
            assertEquals(icnsHeader.length, declared, "icns length");
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

    @Test
    void testLoadWindowIcons() throws Exception {
        List<Image> icons = AppIcons.loadWindowIcons();
        assertTrue(!icons.isEmpty(), "icons loaded");
        assertTrue(icons.get(0).getWidth(null) > 0, "first icon sized");
    }

    @Test
    void testDialogIcon() throws Exception {
        Icon icon = AppIcons.dialogIcon(MainFrame.ABOUT_ICON_SIZE);
        assertTrue(icon != null, "dialog icon present");
        assertEquals(MainFrame.ABOUT_ICON_SIZE, icon.getIconWidth(), "dialog icon width");
        assertEquals(MainFrame.ABOUT_ICON_SIZE, icon.getIconHeight(), "dialog icon height");

        BufferedImage rendered = toImage(icon);
        Image appIcon = null;
        for (Image candidate : AppIcons.loadWindowIcons()) {
            if (candidate.getWidth(null) == MainFrame.ABOUT_ICON_SIZE) {
                appIcon = candidate;
                break;
            }
        }
        assertTrue(appIcon != null, "app icon at dialog size available");
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
        assertTrue(identical, "dialog icon matches application icon");
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

    @Test
    void testAboutMenuItemUsesAppIcon() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testAboutMenuItemUsesAppIcon);
            return;
        }
        MainFrame frame = new MainFrame();
        try {
            JMenuItem about = findMenuItem(frame.getJMenuBar(), "About Lide");
            assertTrue(about != null, "about menu item exists");
            Icon icon = MainFrame.aboutDialogIcon();
            assertTrue(icon != null, "about dialog icon present");
            assertEquals(MainFrame.ABOUT_ICON_SIZE, icon == null ? -1 : icon.getIconWidth(), "about dialog icon size");
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

    @Test
    void testMainFrameHasIcons() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testMainFrameHasIcons);
            return;
        }
        MainFrame frame = new MainFrame();
        try {
            List<Image> icons = frame.getIconImages();
            assertTrue(icons != null && !icons.isEmpty(), "frame icons");
        } finally {
            frame.dispose();
        }
    }

}
