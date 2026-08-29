package lide;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.basic.BasicButtonUI;

/**
 * IntelliJ-inspired dark theme applied to Swing UI defaults.
 */
public final class IdeTheme {
    public static final Color BG = new Color(0x2B2B2B);
    public static final Color BG_RAISED = new Color(0x3C3F41);
    public static final Color BG_TREE = new Color(0x313335);
    public static final Color BG_EDITOR = new Color(0x2B2B2B);
    public static final Color FG = new Color(0xA9B7C6);
    public static final Color FG_DIM = new Color(0x808080);
    /** Near-white — enabled buttons and menu items. */
    public static final Color FG_BRIGHT = new Color(0xF0F0F0);
    /** Clearly faded vs FG_BRIGHT, still readable on dark chrome. */
    public static final Color FG_DISABLED = new Color(0x6A6A6A);
    public static final Color BUTTON_BG = new Color(0x54585A);
    public static final Color BUTTON_BG_DISABLED = new Color(0x2E2E2E);
    public static final Color BUTTON_BORDER = new Color(0x8A9094);
    public static final Color BUTTON_BORDER_DISABLED = new Color(0x3A3A3A);
    public static final Color SELECTION = new Color(0x214283);
    public static final Color CARET = new Color(0xBBBBBB);
    public static final Color BORDER = new Color(0x515151);
    public static final Color LINE_GUTTER = new Color(0x313335);
    public static final Color LINE_NUMBER = new Color(0x606366);
    public static final Color TAB_ACTIVE = new Color(0x4E5254);
    public static final Color ACCENT = new Color(0x3592C4);

    public static final Color KEYWORD = new Color(0xCC7832);
    public static final Color TYPE = new Color(0xFFC66D);
    public static final Color STRING = new Color(0x6A8759);
    public static final Color COMMENT = new Color(0x808080);
    public static final Color NUMBER = new Color(0x6897BB);
    public static final Color ANNOTATION = new Color(0xBBB529);
    public static final Color DEFAULT_TEXT = new Color(0xA9B7C6);

    public static final Font UI_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font EDITOR_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 14);
    public static final Font TREE_FONT = new Font("Segoe UI", Font.PLAIN, 13);

    /** Zero-size icon so menus do not reserve a checkmark/icon column. */
    static final Icon EMPTY_MENU_ICON = new Icon() {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
        }

        @Override
        public int getIconWidth() {
            return 0;
        }

        @Override
        public int getIconHeight() {
            return 0;
        }
    };

    public static void apply() {
        UIManager.put("Panel.background", new ColorUIResource(BG));
        UIManager.put("Panel.foreground", new ColorUIResource(FG));
        UIManager.put("Label.foreground", new ColorUIResource(FG));
        UIManager.put("Label.background", new ColorUIResource(BG));

        applyCompactMenus();

        UIManager.put("Tree.background", new ColorUIResource(BG_TREE));
        UIManager.put("Tree.foreground", new ColorUIResource(FG));
        UIManager.put("Tree.textBackground", new ColorUIResource(BG_TREE));
        UIManager.put("Tree.textForeground", new ColorUIResource(FG));
        UIManager.put("Tree.selectionBackground", new ColorUIResource(SELECTION));
        UIManager.put("Tree.selectionForeground", new ColorUIResource(Color.WHITE));
        UIManager.put("Tree.hash", new ColorUIResource(BORDER));
        UIManager.put("Tree.line", new ColorUIResource(BORDER));
        UIManager.put("Tree.font", new FontUIResource(TREE_FONT));

        UIManager.put("TabbedPane.background", new ColorUIResource(BG));
        UIManager.put("TabbedPane.foreground", new ColorUIResource(FG));
        UIManager.put("TabbedPane.selected", new ColorUIResource(TAB_ACTIVE));
        UIManager.put("TabbedPane.contentAreaColor", new ColorUIResource(BG_EDITOR));
        UIManager.put("TabbedPane.darkShadow", new ColorUIResource(BORDER));
        UIManager.put("TabbedPane.shadow", new ColorUIResource(BORDER));
        UIManager.put("TabbedPane.light", new ColorUIResource(BG_RAISED));
        UIManager.put("TabbedPane.highlight", new ColorUIResource(BG_RAISED));

        UIManager.put("ScrollPane.background", new ColorUIResource(BG));
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("Viewport.background", new ColorUIResource(BG_EDITOR));

        UIManager.put("SplitPane.background", new ColorUIResource(BG));
        UIManager.put("SplitPane.dividerSize", 4);
        UIManager.put("SplitPaneDivider.background", new ColorUIResource(BORDER));

        UIManager.put("TextPane.background", new ColorUIResource(BG_EDITOR));
        UIManager.put("TextPane.foreground", new ColorUIResource(DEFAULT_TEXT));
        UIManager.put("TextPane.caretForeground", new ColorUIResource(CARET));
        UIManager.put("TextPane.selectionBackground", new ColorUIResource(SELECTION));
        UIManager.put("TextPane.selectionForeground", new ColorUIResource(Color.WHITE));
        UIManager.put("TextPane.font", new FontUIResource(EDITOR_FONT));

        UIManager.put("TextArea.background", new ColorUIResource(BG_EDITOR));
        UIManager.put("TextArea.foreground", new ColorUIResource(DEFAULT_TEXT));
        UIManager.put("TextArea.caretForeground", new ColorUIResource(CARET));
        UIManager.put("TextArea.selectionBackground", new ColorUIResource(SELECTION));
        UIManager.put("TextArea.font", new FontUIResource(EDITOR_FONT));

        UIManager.put("Button.background", new ColorUIResource(BUTTON_BG));
        UIManager.put("Button.foreground", new ColorUIResource(FG_BRIGHT));
        UIManager.put("Button.disabledText", new ColorUIResource(FG_DISABLED));
        UIManager.put("Button.disabledForeground", new ColorUIResource(FG_DISABLED));
        UIManager.put("CheckBox.foreground", new ColorUIResource(FG));
        UIManager.put("CheckBox.disabledText", new ColorUIResource(FG_DISABLED));
        UIManager.put("Label.disabledForeground", new ColorUIResource(FG_DISABLED));
        UIManager.put("ToolTip.background", new ColorUIResource(BG_RAISED));
        UIManager.put("ToolTip.foreground", new ColorUIResource(FG));
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER));

        UIManager.put("OptionPane.background", new ColorUIResource(BG));
        UIManager.put("OptionPane.messageForeground", new ColorUIResource(FG));
        UIManager.put("FileChooser.background", new ColorUIResource(BG));

        UIManager.put("defaultFont", new FontUIResource(UI_FONT));
    }

    /**
     * Windows L&F (especially on newer JDKs) reserves a wide left gutter for
     * check/radio icons. Lide menus do not use those, so use basic menu UIs and
     * zero-width check icons for tighter layout.
     */
    static void applyCompactMenus() {
        UIManager.put("MenuBarUI", "javax.swing.plaf.basic.BasicMenuBarUI");
        UIManager.put("MenuItemUI", "javax.swing.plaf.basic.BasicMenuItemUI");
        UIManager.put("MenuUI", "javax.swing.plaf.basic.BasicMenuUI");
        UIManager.put("PopupMenuUI", "javax.swing.plaf.basic.BasicPopupMenuUI");
        UIManager.put("CheckBoxMenuItemUI", "javax.swing.plaf.basic.BasicCheckBoxMenuItemUI");
        UIManager.put("RadioButtonMenuItemUI", "javax.swing.plaf.basic.BasicRadioButtonMenuItemUI");
        UIManager.put("PopupMenuSeparatorUI", "javax.swing.plaf.basic.BasicPopupMenuSeparatorUI");

        UIManager.put("MenuBar.background", new ColorUIResource(BG_RAISED));
        UIManager.put("MenuBar.foreground", new ColorUIResource(FG));
        UIManager.put("MenuBar.border", BorderFactory.createEmptyBorder());
        UIManager.put("Menu.background", new ColorUIResource(BG_RAISED));
        UIManager.put("Menu.foreground", new ColorUIResource(FG));
        UIManager.put("Menu.selectionBackground", new ColorUIResource(SELECTION));
        UIManager.put("Menu.selectionForeground", new ColorUIResource(Color.WHITE));
        UIManager.put("Menu.border", BorderFactory.createEmptyBorder(2, 6, 2, 6));
        UIManager.put("Menu.margin", new InsetsUIResource(0, 0, 0, 0));
        UIManager.put("Menu.acceleratorForeground", new ColorUIResource(FG_DIM));
        UIManager.put("Menu.acceleratorSelectionForeground", new ColorUIResource(Color.WHITE));
        UIManager.put("Menu.disabledForeground", new ColorUIResource(FG_DIM));

        UIManager.put("MenuItem.background", new ColorUIResource(BG_RAISED));
        UIManager.put("MenuItem.foreground", new ColorUIResource(FG));
        UIManager.put("MenuItem.selectionBackground", new ColorUIResource(SELECTION));
        UIManager.put("MenuItem.selectionForeground", new ColorUIResource(Color.WHITE));
        UIManager.put("MenuItem.acceleratorForeground", new ColorUIResource(FG_DIM));
        UIManager.put("MenuItem.acceleratorSelectionForeground", new ColorUIResource(Color.WHITE));
        UIManager.put("MenuItem.disabledForeground", new ColorUIResource(FG_DIM));
        UIManager.put("MenuItem.font", new FontUIResource(UI_FONT));
        UIManager.put("MenuItem.acceleratorFont", new FontUIResource(UI_FONT));
        UIManager.put("MenuItem.border", BorderFactory.createEmptyBorder());
        UIManager.put("MenuItem.margin", new InsetsUIResource(3, 8, 3, 12));
        UIManager.put("MenuItem.checkIcon", EMPTY_MENU_ICON);
        UIManager.put("Menu.checkIcon", EMPTY_MENU_ICON);
        UIManager.put("MenuItem.afterCheckIconGap", 0);
        UIManager.put("MenuItem.checkIconOffset", 0);
        UIManager.put("MenuItem.minimumTextOffset", 0);
        UIManager.put("Menu.afterCheckIconGap", 0);
        UIManager.put("Menu.checkIconOffset", 0);
        UIManager.put("Menu.minimumTextOffset", 0);

        UIManager.put("PopupMenu.background", new ColorUIResource(BG_RAISED));
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("PopupMenu.borderInsets", new Insets(2, 2, 2, 2));
    }

    /**
     * Dark-theme button chrome that actually shows enabled vs disabled.
     * Windows button UI ignores background, so these use basic painting.
     */
    static void styleButton(JButton button) {
        button.setUI(new BasicButtonUI());
        button.setFont(UI_FONT);
        button.setFocusable(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setBorderPainted(true);
        button.addPropertyChangeListener("enabled", e -> applyButtonColors(button));
        applyButtonColors(button);
    }

    static void applyButtonColors(JButton button) {
        boolean on = button.isEnabled();
        button.setForeground(on ? FG_BRIGHT : FG_DISABLED);
        button.setBackground(on ? BUTTON_BG : BUTTON_BG_DISABLED);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(on ? BUTTON_BORDER : BUTTON_BORDER_DISABLED),
                BorderFactory.createEmptyBorder(2, 10, 2, 10)));
    }

    private IdeTheme() {
    }
}
