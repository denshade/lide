package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import java.awt.GraphicsEnvironment;
import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicMenuItemUI;

/**
 * Tests for compact menu spacing.
 */
public final class MenuSpacingTest {

    @Test
    void testCompactMenuDefaults() throws Exception {
        IdeTheme.apply();
        assertEquals("javax.swing.plaf.basic.BasicMenuItemUI", UIManager.get("MenuItemUI"), "MenuItemUI");
        Object checkIcon = UIManager.get("MenuItem.checkIcon");
        assertTrue(checkIcon instanceof Icon, "check icon present");
        assertEquals(0, ((Icon) checkIcon).getIconWidth(), "check icon width");
        assertEquals(0, UIManager.get("MenuItem.afterCheckIconGap"), "afterCheckIconGap");
        assertEquals(0, UIManager.get("MenuItem.minimumTextOffset"), "minimumTextOffset");
        assertEquals(IdeTheme.FG_DIM.getRGB(), UIManager.getColor("MenuItem.disabledForeground").getRGB(), "disabled fg");
        assertEquals(IdeTheme.FG_DISABLED.getRGB(), UIManager.getColor("Button.disabledText").getRGB(), "button disabled text");
    }

    @Test
    void testMenuItemUsesBasicUiAndTightLeft() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "headless environment");
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingSupport.onEdt(this::testMenuItemUsesBasicUiAndTightLeft);
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            AppLog.exception("Could not set system look and feel for menu test", ex);
        }
        IdeTheme.apply();

        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem open = new JMenuItem("Open Directory…");
        JMenuItem disabled = new JMenuItem("Disabled");
        disabled.setEnabled(false);
        file.add(open);
        file.add(disabled);
        bar.add(file);

        assertTrue(open.getUI() instanceof BasicMenuItemUI, "uses basic menu item UI");
        assertEquals(IdeTheme.FG, open.getForeground(), "enabled foreground");

        // Preferred width should stay close to text width (no large check gutter).
        int textWidth = open.getFontMetrics(open.getFont()).stringWidth(open.getText());
        int itemWidth = open.getPreferredSize().width;
        int gutter = itemWidth - textWidth;
        assertTrue(gutter < 40, "left gutter reasonable (< 40px beyond text+padding), was " + gutter);

        assertTrue(luminance(IdeTheme.FG_DIM) < luminance(IdeTheme.FG), "disabled fg darker than enabled");
    }

    @Test
    void testButtonEnabledDisabledContrast() throws Exception {
        javax.swing.JButton on = new javax.swing.JButton("Run");
        javax.swing.JButton off = new javax.swing.JButton("Stop");
        IdeTheme.styleButton(on);
        IdeTheme.styleButton(off);
        off.setEnabled(false);

        assertEquals(IdeTheme.FG_BRIGHT, on.getForeground(), "enabled fg");
        assertEquals(IdeTheme.BUTTON_BG, on.getBackground(), "enabled bg");
        assertEquals(IdeTheme.FG_DISABLED, off.getForeground(), "disabled fg");
        assertEquals(IdeTheme.BUTTON_BG_DISABLED, off.getBackground(), "disabled bg");
        assertTrue(luminance(on.getBackground()) > luminance(off.getBackground()), "enabled fill lighter than disabled");
        assertTrue(luminance(on.getForeground()) > luminance(off.getForeground()) + 80, "enabled label brighter than disabled");
    }

    private static double luminance(java.awt.Color c) {
        return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
    }

}
