package lide;

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
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testCompactMenuDefaults();
        testButtonEnabledDisabledContrast();
        if (!GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeAndWait(MenuSpacingTest::testMenuItemUsesBasicUiAndTightLeft);
        }
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testCompactMenuDefaults() {
        IdeTheme.apply();
        assertEqual("MenuItemUI", "javax.swing.plaf.basic.BasicMenuItemUI",
                UIManager.get("MenuItemUI"));
        Object checkIcon = UIManager.get("MenuItem.checkIcon");
        assertTrue("check icon present", checkIcon instanceof Icon);
        assertEqual("check icon width", 0, ((Icon) checkIcon).getIconWidth());
        assertEqual("afterCheckIconGap", 0, UIManager.get("MenuItem.afterCheckIconGap"));
        assertEqual("minimumTextOffset", 0, UIManager.get("MenuItem.minimumTextOffset"));
        assertEqual("disabled fg", IdeTheme.FG_DIM.getRGB(),
                UIManager.getColor("MenuItem.disabledForeground").getRGB());
        assertEqual("button disabled text", IdeTheme.FG_DISABLED.getRGB(),
                UIManager.getColor("Button.disabledText").getRGB());
    }

    private static void testMenuItemUsesBasicUiAndTightLeft() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
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

        assertTrue("uses basic menu item UI", open.getUI() instanceof BasicMenuItemUI);
        assertEqual("enabled foreground", IdeTheme.FG, open.getForeground());

        // Preferred width should stay close to text width (no large check gutter).
        int textWidth = open.getFontMetrics(open.getFont()).stringWidth(open.getText());
        int itemWidth = open.getPreferredSize().width;
        int gutter = itemWidth - textWidth;
        assertTrue("left gutter reasonable (< 40px beyond text+padding), was " + gutter,
                gutter < 40);

        assertTrue("disabled fg darker than enabled",
                luminance(IdeTheme.FG_DIM) < luminance(IdeTheme.FG));
    }

    private static void testButtonEnabledDisabledContrast() {
        javax.swing.JButton on = new javax.swing.JButton("Run");
        javax.swing.JButton off = new javax.swing.JButton("Stop");
        IdeTheme.styleButton(on);
        IdeTheme.styleButton(off);
        off.setEnabled(false);

        assertEqual("enabled fg", IdeTheme.FG_BRIGHT, on.getForeground());
        assertEqual("enabled bg", IdeTheme.BUTTON_BG, on.getBackground());
        assertEqual("disabled fg", IdeTheme.FG_DISABLED, off.getForeground());
        assertEqual("disabled bg", IdeTheme.BUTTON_BG_DISABLED, off.getBackground());
        assertTrue("enabled fill lighter than disabled",
                luminance(on.getBackground()) > luminance(off.getBackground()));
        assertTrue("enabled label brighter than disabled",
                luminance(on.getForeground()) > luminance(off.getForeground()) + 80);
    }

    private static double luminance(java.awt.Color c) {
        return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
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

    private MenuSpacingTest() {
    }
}
