package lide;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Popup to type a class name and open its source file.
 */
public final class GoToClassDialog {
    private GoToClassDialog() {
    }

    static ClassSearch.Hit show(Window owner, Path projectRoot) {
        List<ClassSearch.Hit> all = ClassSearch.listClasses(projectRoot);
        JDialog dialog = new JDialog(owner, "Go to Class", JDialog.DEFAULT_MODALITY_TYPE);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().setBackground(IdeTheme.BG);

        ClassSearch.Hit[] chosen = {null};
        Panel panel = create(all, hit -> {
            chosen[0] = hit;
            dialog.dispose();
        }, dialog::dispose);
        dialog.setContentPane(panel.root);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        panel.queryField.requestFocusInWindow();
        dialog.setVisible(true);
        return chosen[0];
    }

    static Panel create(List<ClassSearch.Hit> classes, Consumer<ClassSearch.Hit> onOpen, Runnable onCancel) {
        return new Panel(classes, onOpen, onCancel);
    }

    static final class Panel {
        final JPanel root;
        final JTextField queryField;
        final JList<ClassSearch.Hit> results;
        final DefaultListModel<ClassSearch.Hit> model;
        private final List<ClassSearch.Hit> all;
        private final Consumer<ClassSearch.Hit> onOpen;

        Panel(List<ClassSearch.Hit> classes, Consumer<ClassSearch.Hit> onOpen, Runnable onCancel) {
            this.all = classes == null ? List.of() : classes;
            this.onOpen = onOpen == null ? hit -> {
            } : onOpen;
            Runnable cancel = onCancel == null ? () -> {
            } : onCancel;

            root = new JPanel(new BorderLayout(0, 8));
            root.setBackground(IdeTheme.BG);
            root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            root.setPreferredSize(new Dimension(520, 360));

            JLabel hint = new JLabel("Type a class name");
            hint.setForeground(IdeTheme.FG_DIM);
            hint.setFont(IdeTheme.UI_FONT);

            queryField = new JTextField();
            queryField.setFont(IdeTheme.UI_FONT);
            queryField.setBackground(IdeTheme.BG_EDITOR);
            queryField.setForeground(IdeTheme.FG);
            queryField.setCaretColor(IdeTheme.CARET);
            queryField.setSelectionColor(IdeTheme.SELECTION);
            queryField.setSelectedTextColor(Color.WHITE);
            queryField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(IdeTheme.BORDER),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)));

            model = new DefaultListModel<>();
            results = new JList<>(model);
            results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            results.setFont(IdeTheme.UI_FONT);
            results.setBackground(IdeTheme.BG_TREE);
            results.setForeground(IdeTheme.FG);
            results.setSelectionBackground(IdeTheme.SELECTION);
            results.setSelectionForeground(Color.WHITE);
            results.setCellRenderer(new HitRenderer());
            results.setVisibleRowCount(12);

            JScrollPane scroll = new JScrollPane(results);
            scroll.setBorder(BorderFactory.createLineBorder(IdeTheme.BORDER));
            scroll.getViewport().setBackground(IdeTheme.BG_TREE);

            queryField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    refresh();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    refresh();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    refresh();
                }
            });
            queryField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                        moveSelection(1);
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                        moveSelection(-1);
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        chooseSelected();
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        cancel.run();
                        e.consume();
                    }
                }
            });
            results.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        chooseSelected();
                    }
                }
            });
            results.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        chooseSelected();
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        cancel.run();
                        e.consume();
                    }
                }
            });

            JPanel north = new JPanel(new BorderLayout(0, 6));
            north.setOpaque(false);
            north.add(hint, BorderLayout.NORTH);
            north.add(queryField, BorderLayout.CENTER);
            root.add(north, BorderLayout.NORTH);
            root.add(scroll, BorderLayout.CENTER);
        }

        void setQuery(String query) {
            queryField.setText(query == null ? "" : query);
        }

        void refresh() {
            List<ClassSearch.Hit> hits = ClassSearch.filter(all, queryField.getText());
            model.clear();
            for (ClassSearch.Hit hit : hits) {
                model.addElement(hit);
            }
            if (!hits.isEmpty()) {
                results.setSelectedIndex(0);
            }
        }

        void chooseSelected() {
            ClassSearch.Hit hit = results.getSelectedValue();
            if (hit != null) {
                onOpen.accept(hit);
            }
        }

        private void moveSelection(int delta) {
            if (model.isEmpty()) {
                return;
            }
            int next = results.getSelectedIndex() + delta;
            next = Math.max(0, Math.min(model.size() - 1, next));
            results.setSelectedIndex(next);
            results.ensureIndexIsVisible(next);
        }
    }

    private static final class HitRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            label.setFont(IdeTheme.UI_FONT);
            label.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            if (value instanceof ClassSearch.Hit hit) {
                if (selected) {
                    label.setText(hit.className() + "    " + hit.relativePath());
                    label.setForeground(Color.WHITE);
                } else {
                    label.setText("<html><b>" + escape(hit.className())
                            + "</b>&nbsp;&nbsp;<span style='color:#808080'>"
                            + escape(hit.relativePath()) + "</span></html>");
                    label.setForeground(IdeTheme.FG);
                }
            }
            if (!selected) {
                label.setBackground(IdeTheme.BG_TREE);
            }
            label.setOpaque(true);
            return label;
        }

        private static String escape(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
