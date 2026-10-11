package com.modloader;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ClickGui {

    private JFrame frame;
    private volatile boolean visible = false;

    public ClickGui() {
        // must create on EDT
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                build();
            } else {
                SwingUtilities.invokeAndWait(this::build);
            }
        } catch (Exception e) {
            System.out.println("[ClickGui] invokeAndWait failed, trying invokeLater");
            SwingUtilities.invokeLater(this::build);
            try { Thread.sleep(1000); } catch (Exception ignored) {}
        }
    }

    private void build() {
        System.out.println("[ClickGui] Building frame...");

        frame = new JFrame("ClickGui v1.0");
        frame.setSize(480, 370);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        JPanel panel = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();

                // header
                g2.setColor(new Color(20, 20, 38));
                g2.fillRect(0, 0, w, 36);
                g2.setColor(new Color(80, 130, 255));
                g2.setFont(new Font("Arial", Font.BOLD, 16));
                g2.drawString("ClickGui", 14, 24);
                g2.setColor(new Color(100, 100, 130));
                g2.setFont(new Font("Arial", Font.PLAIN, 11));
                g2.drawString("Right Shift = toggle | ESC = close", 120, 24);

                // accent line
                g2.setColor(new Color(80, 130, 255));
                g2.fillRect(0, 36, w, 2);

                // categories
                String[] names = {"Combat", "Movement", "Render", "Player", "World", "Misc"};
                Color[] cols = {
                    new Color(255, 80, 80), new Color(80, 255, 80),
                    new Color(80, 255, 255), new Color(255, 255, 80),
                    new Color(255, 160, 80), new Color(180, 80, 255)
                };

                int y = 48;
                Point mouse = getMousePosition();
                for (int i = 0; i < names.length; i++) {
                    boolean hover = mouse != null && mouse.y >= y && mouse.y < y + 44
                            && mouse.x >= 10 && mouse.x <= w - 10;

                    g2.setColor(hover ? new Color(30, 30, 48) : new Color(22, 22, 36));
                    g2.fillRoundRect(10, y, w - 20, 44, 6, 6);

                    g2.setColor(cols[i]);
                    g2.fillRect(10, y, 3, 44);

                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Arial", Font.BOLD, 14));
                    g2.drawString(names[i], 22, y + 18);

                    g2.setColor(new Color(90, 90, 110));
                    g2.setFont(new Font("Arial", Font.PLAIN, 11));
                    g2.drawString("0 modules", 22, y + 35);
                    g2.drawString(">", w - 28, y + 26);

                    y += 50;
                }
            }
        };
        panel.setBackground(new Color(14, 14, 26));
        panel.setFocusable(true);

        // drag to move
        final int[] dx = {0}, dy = {0};
        panel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                dx[0] = e.getX();
                dy[0] = e.getY();
            }
        });
        panel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                frame.setLocation(frame.getX() + e.getX() - dx[0],
                                  frame.getY() + e.getY() - dy[0]);
            }
        });

        // ESC to hide
        panel.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) hide();
            }
        });

        // repaint timer
        new Timer(80, e -> { if (frame.isVisible()) panel.repaint(); }).start();

        frame.setContentPane(panel);
        System.out.println("[ClickGui] Frame built OK");
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            if (frame == null) return;
            visible = true;
            frame.setVisible(true);
            frame.toFront();
            frame.requestFocus();
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
