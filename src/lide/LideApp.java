package lide;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Entry point for Lide — a lightweight IntelliJ-inspired IDE.
 */
public final class LideApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Fall back to the default look and feel.
            }
            IdeTheme.apply();
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    private LideApp() {
    }
}
