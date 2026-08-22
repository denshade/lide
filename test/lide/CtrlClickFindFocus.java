package lide;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import javax.swing.*;
public class CtrlClickFindFocus {
  public static void main(String[] args) throws Exception {
    if (GraphicsEnvironment.isHeadless()) return;
    Path root = Files.createTempDirectory("lide-ff");
    Path pkg = root.resolve("src/lide");
    Files.createDirectories(pkg);
    Path iconGen = pkg.resolve("IconGenerator.java");
    Path appIcons = pkg.resolve("AppIcons.java");
    Files.writeString(iconGen, "package lide;\npublic class IconGenerator {\n}\n");
    Files.writeString(appIcons, "package lide;\npublic class AppIcons {\n  IconGenerator g;\n}\n");
    SwingUtilities.invokeAndWait(() -> {
      try {
        EditorTabPane pane = new EditorTabPane();
        pane.setProjectRootSupplier(() -> root);
        JFrame f = new JFrame("t");
        f.add(pane);
        f.setSize(900, 700);
        f.setVisible(true);
        pane.openFile(appIcons);
        pane.showFind();
        pane.findBar().setQuery("Icon");
        System.out.println("editor focus="+pane.getActiveEditor().getTextPane().isFocusOwner());
        pane.findBar().getComponent(0).requestFocusInWindow();
        JTextPane tp = pane.getActiveEditor().getTextPane();
        int offset = tp.getText().indexOf("IconGenerator");
        Rectangle r = null;
        for (int i=0;i<30 && r==null;i++) {
          var shape = tp.modelToView2D(offset+2);
          if (shape!=null) r = shape.getBounds();
          try { Thread.sleep(30);} catch(Exception e){}
        }
        Point p = new Point(r.x+2, r.y+r.height/2);
        System.out.println("before sel="+tp.getSelectedText());
        tp.dispatchEvent(new MouseEvent(tp, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK|InputEvent.BUTTON1_DOWN_MASK, p.x,p.y,1,false,MouseEvent.BUTTON1));
        tp.dispatchEvent(new MouseEvent(tp, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK, p.x,p.y,1,false,MouseEvent.BUTTON1));
        tp.dispatchEvent(new MouseEvent(tp, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK, p.x,p.y,1,false,MouseEvent.BUTTON1));
        System.out.println("after path="+pane.getActiveEditor().getFilePath());
        System.out.println("match="+iconGen.toAbsolutePath().normalize().equals(pane.getActiveEditor().getFilePath()));
        f.dispose();
      } catch (Exception ex) { ex.printStackTrace(); }
    });
    try (var w = Files.walk(root)) { w.sorted((a,b)->b.compareTo(a)).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception e){}}); }
  }
}
