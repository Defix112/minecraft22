package com.modloader;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ClickGui {

    private JFrame frame;
    private boolean visible = false;

    public ClickGui() {
        SwingUtilities.invokeLater(this::createGui);
    }

    private void createGui() {
        frame = new JFrame();
        frame.setUndecorated(true);
        frame.setSize(600, 400);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setType(Window.Type.UTILITY);

        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Фон
                g2.setColor(new Color(20, 20, 30, 220));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);

                // Рамка
                g2.setColor(new Color(100, 100, 255, 150));
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 15, 15);

                // Заголовок
                g2.setColor(new Color(100, 150, 255));
                g2.setFont(new Font("Consolas", Font.BOLD, 22));
                g2.drawString("ClickGui", 20, 35);

                // Линия под заголовком
                g2.setColor(new Color(100, 100, 255, 100));
                g2.drawLine(20, 45, getWidth() - 20, 45);

                // Текст-подсказка
                g2.setColor(new Color(150, 150, 170));
                g2.setFont(new Font("Consolas", Font.PLAIN, 14));
                g2.drawString("Пустое меню — добавьте модули", 20, 75);
                g2.drawString("Right Shift — закрыть", 20, 95);

                // Декоративная сетка категорий
                String[] categories = {"Combat", "Movement", "Render", "Player", "World", "Misc"};
                int y = 130;
                for (String cat : categories) {
                    g2.setColor(new Color(40, 40, 60, 200));
                    g2.fillRoundRect(20, y, getWidth() - 40, 35, 8, 8);
                    g2.setColor(new Color(80, 80, 120));
                    g2.drawRoundRect(20, y, getWidth() - 40, 35, 8, 8);
                    g2.setColor(new Color(180, 180, 210));
                    g2.setFont(new Font("Consolas", Font.BOLD, 13));
                    g2.drawString("▸ " + cat, 30, y + 23);
                    g2.setColor(new Color(100, 100, 130));
                    g2.setFont(new Font("Consolas", Font.ITALIC, 11));
                    g2.drawString("пусто", getWidth() - 90, y + 23);
                    y += 42;
                }

                g2.dispose();
            }
        };
        mainPanel.setOpaque(false);
        mainPanel.setLayout(null);

        // Перетаскивание окна
        final Point[] dragOffset = {null};
        mainPanel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                dragOffset[0] = e.getPoint();
            }
        });
        mainPanel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (dragOffset[0] != null) {
                    Point loc = frame.getLocation();
                    frame.setLocation(
                        loc.x + e.getX() - dragOffset[0].x,
                        loc.y + e.getY() - dragOffset[0].y
                    );
                }
            }
        });

        frame.setContentPane(mainPanel);
        frame.setVisible(false);
    }

    public void toggle() {
        if (frame == null) return;
        SwingUtilities.invokeLater(() -> {
            visible = !visible;
            frame.setVisible(visible);
            if (visible) {
                frame.toFront();
                System.out.println("[ModLoader] ClickGui открыт");
            } else {
                System.out.println("[ModLoader] ClickGui закрыт");
            }
        });
    }
}
