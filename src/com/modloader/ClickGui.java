package com.modloader;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ClickGui {

    private static volatile ClickGui instance;
    private JFrame frame;
    private volatile boolean visible = false;

    public static ClickGui getInstance() {
        if (instance == null) {
            synchronized (ClickGui.class) {
                if (instance == null) {
                    instance = new ClickGui();
                }
            }
        }
        return instance;
    }

    private ClickGui() {
        try {
            SwingUtilities.invokeAndWait(this::build);
        } catch (Exception e) {
            System.out.println("[ClickGui] Failed to create GUI: " + e);
            e.printStackTrace(System.out);
        }
    }

    private void build() {
        frame = new JFrame();
        frame.setTitle("ClickGui");
        frame.setUndecorated(true);
        frame.setSize(480, 360);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
        frame.setResizable(false);

        // dark background - no transparency tricks
        JPanel panel = new JPanel(null) {
            final String[] cats = {"Combat", "Movement", "Render", "Player", "World", "Misc"};
            final Color[] accents = {
                new Color(255, 85, 85),
                new Color(85, 255, 85),
                new Color(85, 255, 255),
                new Color(255, 255, 85),
                new Color(255, 153, 85),
                new Color(170, 85, 255)
            };

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // header
                g2.setColor(new Color(20, 20, 35));
                g2.fillRect(0, 0, getWidth(), 38);

                g2.setColor(new Color(90, 140, 255));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 17));
                g2.drawString("ClickGui", 14, 25);

                g2.setColor(new Color(90, 90, 120));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g2.drawString("Right Shift = toggle | drag to move", 120, 25);

                // line
                g2.setColor(new Color(70, 100, 255, 120));
                g2.fillRect(0, 38, getWidth(), 2);

                // categories
                int y = 50;
                Point mouse = getMousePosition();
                for (int i = 0; i < cats.length; i++) {
                    boolean hovered = mouse != null
                            && mouse.x >= 10 && mouse.x <= getWidth() - 10
                            && mouse.y >= y && mouse.y <= y + 42;

                    g2.setColor(hovered ? new Color(32, 32, 50) : new Color(24, 24, 38));
                    g2.fillRoundRect(10, y, getWidth() - 20, 42, 6, 6);

                    // accent
                    g2.setColor(accents[i]);
                    g2.fillRect(10, y, 3, 42);

                    // name
                    g2.setColor(new Color(210, 210, 230));
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                    g2.drawString(cats[i], 24, y + 18);

                    // sub
                    g2.setColor(new Color(80, 80, 110));
                    g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                    g2.drawString("0 modules", 24, y + 34);

                    // arrow
                    g2.setColor(new Color(60, 60, 90));
                    g2.drawString(">", getWidth() - 30, y + 26);

                    y += 48;
                }
            }
        };
        panel.setBackground(new Color(16, 16, 28));

        // drag
        final int[] mx = {0}, my = {0};
        panel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { mx[0] = e.getX(); my[0] = e.getY(); }
        });
        panel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                frame.setLocation(
                    frame.getX() + e.getX() - mx[0],
                    frame.getY() + e.getY() - my[0]);
            }
        });

        // ESC to close
        panel.setFocusable(true);
        panel.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) toggle();
            }
        });

        // repaint for hover
        new Timer(60, e -> { if (frame.isVisible()) panel.repaint(); }).start();

        frame.setContentPane(panel);
        frame.setVisible(false);
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            visible = true;
            frame.setVisible(true);
            frame.toFront();
            System.out.println("[ClickGui] OPENED");
        });
    }

    public void toggle() {
        SwingUtilities.invokeLater(() -> {
            visible = !visible;
            frame.setVisible(visible);
            if (visible) {
                frame.toFront();
                System.out.println("[ClickGui] OPENED");
            } else {
                System.out.println("[ClickGui] CLOSED");
            }
        });
    }
}
