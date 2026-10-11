package com.modloader;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;

public class ClickGui {

    private static ClickGui instance;
    private JFrame frame;
    private boolean visible = false;

    public static ClickGui getInstance() {
        if (instance == null) {
            instance = new ClickGui();
        }
        return instance;
    }

    private ClickGui() {
        try {
            SwingUtilities.invokeAndWait(this::createGui);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createGui() {
        frame = new JFrame("ClickGui");
        frame.setUndecorated(true);
        frame.setSize(520, 380);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setType(Window.Type.UTILITY);
        frame.setFocusableWindowState(false);

        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                g2.setColor(new Color(15, 15, 25, 230));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));

                g2.setColor(new Color(90, 120, 255, 200));
                g2.setStroke(new BasicStroke(2));
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 12, 12));

                // header bar
                g2.setColor(new Color(25, 25, 45, 255));
                g2.fillRoundRect(0, 0, getWidth(), 40, 12, 12);
                g2.fillRect(0, 28, getWidth(), 12);

                // title
                g2.setColor(new Color(90, 140, 255));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
                g2.drawString("ClickGui", 16, 27);

                // version
                g2.setColor(new Color(100, 100, 140));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g2.drawString("v1.0 | Right Shift to close", 110, 27);

                // separator
                g2.setColor(new Color(90, 120, 255, 60));
                g2.fillRect(10, 42, getWidth() - 20, 1);

                // categories
                String[] cats = {"Combat", "Movement", "Render", "Player", "World", "Misc"};
                int[] colors = {0xFFFF5555, 0xFF55FF55, 0xFF55FFFF, 0xFFFFFF55, 0xFFFF9955, 0xFFAA55FF};

                int y = 55;
                for (int i = 0; i < cats.length; i++) {
                    boolean hovered = false;
                    Point mouse = getMousePosition();
                    if (mouse != null) {
                        hovered = mouse.x >= 12 && mouse.x <= getWidth() - 12
                                && mouse.y >= y && mouse.y <= y + 44;
                    }

                    // card bg
                    g2.setColor(hovered ? new Color(35, 35, 55) : new Color(22, 22, 38));
                    g2.fillRoundRect(12, y, getWidth() - 24, 44, 8, 8);

                    // accent bar
                    g2.setColor(new Color(colors[i]));
                    g2.fillRoundRect(12, y, 4, 44, 4, 4);

                    // category name
                    g2.setColor(new Color(210, 210, 230));
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                    g2.drawString(cats[i], 26, y + 19);

                    // status
                    g2.setColor(new Color(80, 80, 110));
                    g2.setFont(new Font("Segoe UI", Font.ITALIC, 11));
                    g2.drawString("0 modules", 26, y + 36);

                    // arrow
                    g2.setColor(new Color(60, 60, 90));
                    g2.setFont(new Font("Segoe UI", Font.PLAIN, 16));
                    g2.drawString(">", getWidth() - 35, y + 28);

                    y += 50;
                }

                g2.dispose();
            }
        };
        mainPanel.setOpaque(false);
        mainPanel.setLayout(null);

        // drag support
        final Point[] offset = {null};
        mainPanel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                offset[0] = e.getPoint();
                frame.setFocusableWindowState(true);
            }
            public void mouseReleased(MouseEvent e) {
                frame.setFocusableWindowState(false);
            }
        });
        mainPanel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (offset[0] != null) {
                    Point loc = frame.getLocation();
                    frame.setLocation(loc.x + e.getX() - offset[0].x,
                                      loc.y + e.getY() - offset[0].y);
                }
            }
        });

        // repaint timer for hover effects
        Timer repaintTimer = new Timer(50, e -> {
            if (frame.isVisible()) mainPanel.repaint();
        });
        repaintTimer.start();

        frame.setContentPane(mainPanel);
        frame.setVisible(false);
    }

    public void toggle() {
        SwingUtilities.invokeLater(() -> {
            visible = !visible;
            frame.setVisible(visible);
            if (visible) {
                frame.toFront();
                System.out.println("[ClickGui] Opened");
            } else {
                System.out.println("[ClickGui] Closed");
            }
        });
    }

    public boolean isVisible() {
        return visible;
    }
}
