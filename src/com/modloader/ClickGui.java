package com.modloader;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import javax.swing.*;
import java.util.List;

public class ClickGui {

    private JFrame frame;
    private volatile boolean visible = false;
    private ModuleManager modules;
    private int selectedCat = 0;

    static final String[] CAT_NAMES = {"Combat", "Movement", "Render", "Player", "World", "Misc"};
    static final Color[] CAT_COLORS = {
        new Color(255, 75, 75), new Color(75, 255, 120),
        new Color(75, 200, 255), new Color(255, 230, 75),
        new Color(255, 150, 75), new Color(170, 75, 255)
    };

    private static final int PW = 560, PH = 380;

    public ClickGui(ModuleManager modules) {
        this.modules = modules;
        try {
            if (SwingUtilities.isEventDispatchThread()) build();
            else SwingUtilities.invokeAndWait(this::build);
        } catch (Exception e) {
            System.out.println("[ClickGui] invokeAndWait failed, trying invokeLater");
            SwingUtilities.invokeLater(this::build);
            try { Thread.sleep(1500); } catch (Exception ignored) {}
        }
    }

    private void build() {
        frame = new JFrame();
        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);
        frame.setType(Window.Type.UTILITY);
        frame.setSize(PW, PH);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        try { frame.setOpacity(0.96f); } catch (Exception ignored) {}

        JPanel panel = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();

                // background
                g2.setColor(new Color(12, 12, 24));
                g2.fillRoundRect(0, 0, w, h, 12, 12);

                // border
                g2.setColor(new Color(40, 40, 65));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);

                // header
                g2.setColor(new Color(18, 18, 34));
                g2.fillRoundRect(1, 1, w - 2, 34, 12, 12);
                g2.fillRect(1, 24, w - 2, 11);

                GradientPaint grad = new GradientPaint(0, 0, new Color(80, 130, 255), w, 0, new Color(140, 80, 255));
                g2.setPaint(grad);
                g2.fillRect(1, 35, w - 2, 2);

                g2.setColor(new Color(80, 140, 255));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
                g2.drawString("ClickGui", 12, 23);

                g2.setColor(new Color(70, 70, 100));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                g2.drawString("RShift = toggle  |  ESC = close  |  Drag to move", 115, 23);

                // close btn
                Point mouse = getMousePosition();
                int cbx = w - 26, cby = 8;
                boolean hClose = mouse != null && mouse.x >= cbx && mouse.x <= cbx + 18 && mouse.y >= cby && mouse.y <= cby + 18;
                g2.setColor(hClose ? new Color(255, 50, 50) : new Color(90, 45, 45));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
                g2.drawString("X", cbx + 3, cby + 14);

                // === LEFT: categories ===
                int leftX = 6, leftW = 126;
                int catY = 44;
                for (int i = 0; i < CAT_NAMES.length; i++) {
                    int cy = catY + i * 52;
                    boolean sel = (i == selectedCat);
                    boolean hover = mouse != null && mouse.x >= leftX && mouse.x <= leftX + leftW
                            && mouse.y >= cy && mouse.y < cy + 46;

                    g2.setColor(sel ? new Color(28, 28, 50) : hover ? new Color(22, 22, 42) : new Color(16, 16, 32));
                    g2.fillRoundRect(leftX, cy, leftW, 46, 6, 6);

                    if (sel) {
                        g2.setColor(CAT_COLORS[i]);
                        g2.fillRoundRect(leftX, cy, 3, 46, 2, 2);
                    }

                    g2.setColor(CAT_COLORS[i]);
                    g2.fillOval(leftX + 12, cy + 16, 8, 8);

                    g2.setColor(sel ? Color.WHITE : new Color(180, 180, 200));
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    g2.drawString(CAT_NAMES[i], leftX + 28, cy + 22);

                    int en = modules.enabledCount(CAT_NAMES[i]);
                    if (en > 0) {
                        g2.setColor(new Color(60, 200, 100));
                        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                        g2.drawString(en + " on", leftX + 28, cy + 38);
                    }
                }

                // === RIGHT: modules ===
                int rightX = 140, rightW = w - 148;
                int modY = 44;

                List<Module> mods = modules.get(CAT_NAMES[selectedCat]);

                if (mods.isEmpty()) {
                    g2.setColor(new Color(55, 55, 80));
                    g2.setFont(new Font("Segoe UI", Font.ITALIC, 12));
                    g2.drawString("No modules yet", rightX + 20, modY + 30);
                }

                for (int i = 0; i < mods.size(); i++) {
                    Module m = mods.get(i);
                    int my = modY + i * 50;
                    boolean hover = mouse != null && mouse.x >= rightX && mouse.x <= rightX + rightW
                            && mouse.y >= my && mouse.y < my + 44;

                    g2.setColor(hover ? new Color(26, 26, 46) : new Color(18, 18, 34));
                    g2.fillRoundRect(rightX, my, rightW, 44, 6, 6);

                    if (m.enabled) {
                        g2.setColor(new Color(60, 200, 100, 30));
                        g2.fillRoundRect(rightX, my, rightW, 44, 6, 6);
                    }

                    // toggle
                    int tx = rightX + rightW - 50, ty = my + 12;
                    if (m.enabled) {
                        g2.setColor(new Color(60, 200, 100));
                        g2.fillRoundRect(tx, ty, 38, 20, 10, 10);
                        g2.setColor(Color.WHITE);
                        g2.fillOval(tx + 20, ty + 2, 16, 16);
                    } else {
                        g2.setColor(new Color(50, 50, 70));
                        g2.fillRoundRect(tx, ty, 38, 20, 10, 10);
                        g2.setColor(new Color(80, 80, 100));
                        g2.fillOval(tx + 2, ty + 2, 16, 16);
                    }

                    g2.setColor(m.enabled ? Color.WHITE : new Color(160, 160, 180));
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
                    g2.drawString(m.name, rightX + 12, my + 18);

                    g2.setColor(new Color(65, 65, 90));
                    g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                    g2.drawString(m.desc, rightX + 12, my + 35);
                }

                // version
                g2.setColor(new Color(40, 40, 60));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                g2.drawString("v1.0", w - 28, h - 6);
            }
        };
        panel.setBackground(new Color(12, 12, 24));
        panel.setFocusable(true);

        // drag header to move
        final int[] dragX = {0}, dragY = {0};
        final boolean[] dragging = {false};
        panel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int mx = e.getX(), my = e.getY();

                // close
                if (mx >= panel.getWidth() - 26 && mx <= panel.getWidth() - 8 && my >= 8 && my <= 26) {
                    hide();
                    return;
                }

                // drag zone (header)
                if (my <= 37) {
                    dragging[0] = true;
                    dragX[0] = mx;
                    dragY[0] = my;
                    return;
                }

                // category click
                int leftX = 6, leftW = 126, catY = 44;
                for (int i = 0; i < CAT_NAMES.length; i++) {
                    int cy = catY + i * 52;
                    if (mx >= leftX && mx <= leftX + leftW && my >= cy && my < cy + 46) {
                        selectedCat = i;
                        return;
                    }
                }

                // module click
                int rightX = 140, rightW = panel.getWidth() - 148, modY = 44;
                List<Module> mods = modules.get(CAT_NAMES[selectedCat]);
                for (int i = 0; i < mods.size(); i++) {
                    int cy = modY + i * 50;
                    if (mx >= rightX && mx <= rightX + rightW && my >= cy && my < cy + 44) {
                        mods.get(i).toggle();
                        return;
                    }
                }
            }

            public void mouseReleased(MouseEvent e) {
                dragging[0] = false;
            }
        });

        panel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (dragging[0]) {
                    frame.setLocation(frame.getX() + e.getX() - dragX[0],
                                      frame.getY() + e.getY() - dragY[0]);
                }
            }
        });

        panel.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) hide();
            }
        });

        new Timer(50, e -> { if (frame.isVisible()) panel.repaint(); }).start();

        // periodically ensure always on top (for F11 fullscreen)
        new Timer(500, e -> {
            if (frame.isVisible()) {
                frame.setAlwaysOnTop(true);
                frame.toFront();
            }
        }).start();

        frame.setContentPane(panel);
        System.out.println("[ClickGui] Built OK");
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            if (frame == null) return;
            visible = true;
            try { new File(System.getProperty("java.io.tmpdir"), "clickgui_pause.txt").createNewFile(); }
            catch (Exception ignored) {}
            frame.setAlwaysOnTop(true);
            frame.setVisible(true);
            frame.toFront();
            frame.requestFocus();
            frame.getContentPane().requestFocusInWindow();
        });
    }

    public void hide() {
        SwingUtilities.invokeLater(() -> {
            if (frame == null) return;
            visible = false;
            new File(System.getProperty("java.io.tmpdir"), "clickgui_pause.txt").delete();
            frame.setVisible(false);
        });
    }

    public void toggle() {
        if (visible) hide(); else show();
    }

    public boolean isVisible() {
        return visible;
    }
}
