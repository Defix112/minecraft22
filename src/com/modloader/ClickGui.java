package com.modloader;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
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
            SwingUtilities.invokeAndWait(this::createGui);
            System.out.println("[ClickGui] GUI created successfully");
        } catch (Exception e) {
            System.out.println("[ClickGui] GUI creation error: " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }

    private void createGui() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        frame = new JFrame("ClickGui");
        frame.setUndecorated(true);
        frame.setSize(520, 380);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
        frame.setType(Window.Type.UTILITY);

        // make it semi-transparent
        frame.setBackground(new Color(0, 0, 0, 0));

        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // background
                g2.setColor(new Color(15, 15, 25, 235));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));

                // border
                g2.setColor(new Color(90, 120, 255, 200));
                g2.setStroke(new BasicStroke(2));
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 12, 12));

                // header bar
                g2.setColor(new Color(25, 25, 45));
                g2.fillRoundRect(0, 0, getWidth(), 40, 12, 12);
                g2.fillRect(0, 28, getWidth(), 12);

                // title
                g2.setColor(new Color(90, 140, 255));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
                g2.drawString("ClickGui", 16, 27);

                // hint
                g2.setColor(new Color(100, 100, 140));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g2.drawString("RShift / ESC to close | drag to move", 110, 27);

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

                    g2.setColor(hovered ? new Color(35, 35, 55) : new Color(22, 22, 38));
                    g2.fillRoundRect(12, y, getWidth() - 24, 44, 8, 8);

                    g2.setColor(new Color(colors[i]));
                    g2.fillRoundRect(12, y, 4, 44, 4, 4);

                    g2.setColor(new Color(210, 210, 230));
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                    g2.drawString(cats[i], 26, y + 19);

                    g2.setColor(new Color(80, 80, 110));
                    g2.setFont(new Font("Segoe UI", Font.ITALIC, 11));
                    g2.drawString("0 modules", 26, y + 36);

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

        // drag
        final Point[] offset = {null};
        mainPanel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { offset[0] = e.getPoint(); }
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

        // close on ESC
        frame.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    toggle();
                }
            }
        });

        // repaint timer
        Timer timer = new Timer(50, e -> { if (frame.isVisible()) mainPanel.repaint(); });
        timer.start();

        frame.setContentPane(mainPanel);
        frame.setVisible(false);
    }

    public void toggle() {
        SwingUtilities.invokeLater(() -> {
            visible = !visible;
            frame.setVisible(visible);
            if (visible) {
                frame.toFront();
                frame.requestFocus();
                System.out.println("[ClickGui] OPENED");
            } else {
                System.out.println("[ClickGui] CLOSED");
            }
        });
    }

    public boolean isVisible() {
        return visible;
    }
}
