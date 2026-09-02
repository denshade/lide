package lide;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * Loads application icon images from the classpath.
 */
public final class AppIcons {
    private static final String[] RESOURCE_PATHS = {
            "/lide/icons/lide-16.png",
            "/lide/icons/lide-32.png",
            "/lide/icons/lide-64.png",
            "/lide/icons/lide-128.png",
            "/lide/icons/lide-256.png"
    };

    private AppIcons() {
    }

    public static List<Image> loadWindowIcons() {
        List<Image> icons = new ArrayList<>();
        for (String path : RESOURCE_PATHS) {
            Image image = load(path);
            if (image != null) {
                icons.add(image);
            }
        }
        if (icons.isEmpty()) {
            icons.add(IconGenerator.render(32));
            icons.add(IconGenerator.render(64));
            icons.add(IconGenerator.render(128));
        }
        return icons;
    }

    /**
     * Returns the application icon scaled to {@code size} pixels, for use in dialogs.
     */
    public static Icon dialogIcon(int size) {
        Image best = null;
        int bestWidth = 0;
        for (Image image : loadWindowIcons()) {
            int width = image.getWidth(null);
            if (width <= 0) {
                continue;
            }
            boolean betterFit = best == null
                    || (bestWidth < size && width > bestWidth)
                    || (width >= size && width < bestWidth);
            if (betterFit) {
                best = image;
                bestWidth = width;
            }
        }
        if (best == null) {
            best = IconGenerator.render(size);
            bestWidth = size;
        }
        if (bestWidth != size) {
            best = best.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        }
        return new ImageIcon(best);
    }

    static Image load(String resourcePath) {
        try (InputStream in = AppIcons.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            BufferedImage image = ImageIO.read(in);
            return image;
        } catch (IOException ex) {
            AppLog.exception("Could not load icon " + resourcePath, ex);
            return null;
        }
    }
}
