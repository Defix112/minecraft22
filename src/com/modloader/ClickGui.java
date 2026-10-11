package com.modloader;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ClickGui {

    private JFrame frame;
    private volatile boolean visible = false;

    public ClickGui() {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                build();
            } else {
                SwingUtilities.invokeAndWait(this::build);
            }
        } catch (Exception e) {
            System.out.println("[ClickGui] invokeAndWait failed, trying invokeLater");
            SwingUtilities.invokeLater(this::build);
            try { Thread.sleep(1500); } catch (Exception ignored) {}
        }
    }

    private void build() {
        System.out.println("[ClickGui] Building overlay...");

        frame = new JFrame();
        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);
        frame.setType(Window.Type.UTILITY);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize(screen.width, screen.height);
        frame.setLocation(0, 0);

        boolean transparent = false;
        try {
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice();
            if (gd.isWindowTranslucencySupported(
                    GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                frame.setBackground(new Color(0, 0, 0, 0));
                transparent = true;
            }
        } catch (Exception e) {
            System.out.println("[ClickGui] Transparency check failed: " + e.getMessage());
        }

        final boolean isTransparent = transparent;

        JPanel panel = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();

                if (isTransparent) {
                    g2.setComposite(AlphaComposite.Clear);
                    g2.fillRect(0, 0, w, h);
                    g2.setComposite(AlphaComposite.SrcOver);
                    g2.setColor(new Color(0, 0, 0, 100));
                    g2.fillRect(0, 0, w, h);
                } else {
                    g2.setColor(new Color(8, 8, 16));
                    g2.fillRect(0, 0, w, h);
                }

                int pw = 520, ph = 370;
                int px = (w - pw) / 2, py = (h - ph) / 2;

                g2.setColor(new Color(0, 0, 0, 60));
                g2.fillRoundRect(px + 4, py + 4, pw, ph, 10, 10);

                g2.setColor(new Color(14, 14, 28, 245));
                g2.fillRoundRect(px, py, pw, ph, 10, 10);

                g2.setColor(new Color(20, 20, 38, 250));
                g2.fillRoundRect(px, py, pw, 38, 10, 10);
                g2.fillRect(px, py + 28, pw, 10);

                GradientPaint grad = new GradientPaint(
                        px, py + 38, new Color(80, 130, 255),
                        px + pw, py + 38, new Color(140, 80, 255));
                g2.setPaint(grad);
                g2.fillRect(px, py + 38, pw, 2);

                g2.setColor(new Color(80, 140, 255));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
                g2.drawString("ClickGui", px + 14, py + 25);

                g2.setColor(new Color(85, 85, 115));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g2.drawString("RShift = toggle  |  ESC = close", px + 130, py + 25);

                Point mouse = getMousePosition();

                int cbx = px + pw - 30, cby = py + 10;
                boolean hoverClose = mouse != null
                        && mouse.x >= cbx && mouse.x <= cbx + 20
                        && mouse.y >= cby && mouse.y <= cby + 20;
                g2.setColor(hoverClose ? new Color(255, 60, 60) : new Color(100, 50, 50));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                g2.drawString("X", cbx + 4, cby + 15);

                String[] names = {"Combat", "Movement", "Render", "Player", "World", "Misc"};
                Color[] cols = {
                    new Color(255, 75, 75), new Color(75, 255, 120),
                    new Color(75, 200, 255), new Color(255, 230, 75),
                    new Color(255, 150, 75), new Color(170, 75, 255)
                };

                int cy = py + 50;
                for (int i = 0; i < names.length; i++) {
                    int cardX = px + 10;
                    int cardW = pw - 20;
                    int cardH = 46;

                    boolean hover = mouse != null
                            && mouse.x >= cardX && mouse.x <= cardX + cardW
                            && mouse.y >= cy && mouse.y < cy + cardH;

                    g2.setColor(hover ? new Color(30, 30, 50, 235) : new Color(20, 20, 36, 220));
                    g2.fillRoundRect(cardX, cy, cardW, cardH, 6, 6);

                    g2.setColor(cols[i]);
                    g2.fillRoundRect(cardX, cy, 4, cardH, 2, 2);

                    g2.setColor(cols[i]);
                    g2.fillOval(cardX + 16, cy + 14, 8, 8);

                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                    g2.drawString(names[i], cardX + 34, cy + 20);

                    g2.setColor(new Color(75, 75, 100));
                    g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                    g2.drawString("0 modules", cardX + 34, cy + 37);

                    g2.setColor(hover ? new Color(130, 130, 170) : new Color(60, 60, 90));
                    g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                    g2.drawString("▶", cardX + cardW - 22, cy + 28);

                    cy += 52;
                }

                g2.setColor(new Color(50, 50, 75));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                g2.drawString("v1.0", px + pw - 35, py + ph - 8);
            }
        };
        panel.setOpaque(false);
        panel.setFocusable(true);

        panel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int w = panel.getWidth(), h = panel.getHeight();
                int pw = 520;
                int px = (w - pw) / 2, py = (h - 370) / 2;
                int cbx = px + pw - 30, cby = py + 10;
                if (e.getX() >= cbx && e.getX() <= cbx + 20
                        && e.getY() >= cby && e.getY() <= cby + 20) {
                    hide();
                }
            }
        });

        panel.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) hide();
            }
        });

        new Timer(50, e -> { if (frame.isVisible()) panel.repaint(); }).start();

        frame.setContentPane(panel);
        System.out.println("[ClickGui] Overlay built OK (transparent=" + transparent + ")");
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            if (frame == null) return;
            visible = true;
            frame.setVisible(true);
            frame.toFront();
            frame.requestFocus();
            frame.getContentPane().requestFocusInWindow();
            System.out.println("[ClickGui] SHOWN");
        });
    }

    public void hide() {
        SwingUtilities.invokeLater(() -> {
            if (frame == null) return;
            visible = false;
            frame.setVisible(false);
            System.out.println("[ClickGui] HIDDEN");
        });
    }

    public void toggle() {
        if (visible) hide(); else show();
    }

    public boolean isVisible() {
        return visible;
    }
}
