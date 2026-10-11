package com.modloader;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ClickGuiScreen extends Screen {

    private static final String[] CATEGORIES = {"Combat", "Movement", "Render", "Player", "World", "Misc"};

    public ClickGuiScreen() {
        super(Text.literal("ClickGui"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dark overlay background
        context.fill(0, 0, this.width, this.height, 0x88000000);

        int panelWidth = 500;
        int panelHeight = 320;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        // Main panel background
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xDD141420);

        // Border
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF6464FF);
        context.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF6464FF);
        context.fill(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF6464FF);
        context.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF6464FF);

        // Title
        context.drawText(this.textRenderer, "ClickGui", panelX + 15, panelY + 10, 0xFF6496FF, true);

        // Separator line
        context.fill(panelX + 10, panelY + 25, panelX + panelWidth - 10, panelY + 26, 0x606464FF);

        // Hint text
        context.drawText(this.textRenderer, "Empty menu - add modules", panelX + 15, panelY + 35, 0xFF969AAA, false);
        context.drawText(this.textRenderer, "Right Shift - close", panelX + 15, panelY + 48, 0xFF969AAA, false);

        // Category rows
        int rowY = panelY + 70;
        for (String category : CATEGORIES) {
            boolean hovered = mouseX >= panelX + 15 && mouseX <= panelX + panelWidth - 15
                    && mouseY >= rowY && mouseY <= rowY + 30;

            // Row background
            int bgColor = hovered ? 0xCC303050 : 0xAA28283C;
            context.fill(panelX + 15, rowY, panelX + panelWidth - 15, rowY + 30, bgColor);

            // Row border
            int borderColor = hovered ? 0xFF8080C0 : 0xFF505078;
            context.fill(panelX + 15, rowY, panelX + panelWidth - 15, rowY + 1, borderColor);
            context.fill(panelX + 15, rowY + 29, panelX + panelWidth - 15, rowY + 30, borderColor);
            context.fill(panelX + 15, rowY, panelX + 16, rowY + 30, borderColor);
            context.fill(panelX + panelWidth - 16, rowY, panelX + panelWidth - 15, rowY + 30, borderColor);

            // Category name
            context.drawText(this.textRenderer, "> " + category, panelX + 25, rowY + 10, 0xFFB4B4D2, true);

            // Empty label
            context.drawText(this.textRenderer, "empty", panelX + panelWidth - 70, rowY + 10, 0xFF646482, false);

            rowY += 38;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
