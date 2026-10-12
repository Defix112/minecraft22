package com.modloader;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

// Private members use a "cg" prefix: they are remapped against Screen's hierarchy and must not collide with game names.
public class ClickGuiScreen extends Screen {

    private static final int CG_W = 300, CG_H = 190;
    private static final int CG_SIDE = 80, CG_HEAD = 24, CG_FOOT = 20;
    private static final int CG_CAT_H = 20, CG_CAT_GAP = 2;
    private static final int CG_ROW_H = 24, CG_ROW_GAP = 4;
    private static final int CG_PANIC_W = 48, CG_PANIC_H = 12;
    private static final int CG_KEY_RSHIFT = 344;

    private static Category cgSelected = Category.COMBAT;

    public ClickGuiScreen() {
        super(Component.literal("System Client"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    private int cgLeft() { return (this.width - CG_W) / 2; }
    private int cgTop() { return (this.height - CG_H) / 2; }
    private int cgCatY(int i) { return cgTop() + CG_HEAD + 6 + i * (CG_CAT_H + CG_CAT_GAP); }
    private int cgRowX() { return cgLeft() + CG_SIDE + 8; }
    private int cgRowW() { return CG_W - CG_SIDE - 16; }
    private int cgRowY(int i) { return cgTop() + CG_HEAD + 8 + i * (CG_ROW_H + CG_ROW_GAP); }
    private int cgPanicX() { return cgLeft() + CG_W - CG_PANIC_W - 6; }
    private int cgPanicY() { return cgTop() + CG_H - CG_FOOT + 4; }

    private static boolean cgInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = cgLeft(), y = cgTop();

        g.fill(0, 0, this.width, this.height, Theme.DIM);
        g.fill(x - 2, y - 2, x + CG_W + 2, y + CG_H + 2, Theme.SHADOW);
        g.fill(x, y, x + CG_W, y + CG_H, Theme.BG);

        g.fill(x, y, x + CG_W, y + CG_HEAD, Theme.HEADER);
        g.fill(x, y, x + CG_W, y + 2, Theme.ACCENT);
        g.drawString(this.font, "System", x + 8, y + 9, Theme.ACCENT, false);
        g.drawString(this.font, "Client", x + 8 + this.font.width("System "), y + 9, Theme.TEXT, false);
        String hint = "RShift / Esc";
        g.drawString(this.font, hint, x + CG_W - 8 - this.font.width(hint), y + 9, Theme.TEXT_FAINT, false);
        g.fill(x, y + CG_HEAD, x + CG_W, y + CG_HEAD + 1, Theme.BORDER);

        int bodyTop = y + CG_HEAD + 1, footTop = y + CG_H - CG_FOOT;
        g.fill(x, bodyTop, x + CG_SIDE, footTop, Theme.SIDEBAR);
        g.fill(x + CG_SIDE, bodyTop, x + CG_SIDE + 1, footTop, Theme.BORDER);

        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            Category c = cats[i];
            int cy = cgCatY(i);
            boolean sel = c == cgSelected;
            boolean hov = cgInside(mouseX, mouseY, x, cy, CG_SIDE, CG_CAT_H);
            if (sel) {
                g.fill(x, cy, x + CG_SIDE, cy + CG_CAT_H, Theme.ACCENT_SOFT);
                g.fill(x, cy, x + 2, cy + CG_CAT_H, Theme.ACCENT);
            } else if (hov) {
                g.fill(x, cy, x + CG_SIDE, cy + CG_CAT_H, Theme.HOVER);
            }
            g.drawString(this.font, c.title, x + 10, cy + 6, sel ? Theme.TEXT : hov ? Theme.TEXT_MID : Theme.TEXT_DIM, false);
            String count = String.valueOf(ModuleManager.byCategory(c).size());
            g.drawString(this.font, count, x + CG_SIDE - 8 - this.font.width(count), cy + 6, Theme.TEXT_FAINT, false);
        }

        List<Module> mods = ModuleManager.byCategory(cgSelected);
        int rx = cgRowX(), rw = cgRowW();
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int ry = cgRowY(i);
            boolean on = m.isEnabled();
            boolean hov = cgInside(mouseX, mouseY, rx, ry, rw, CG_ROW_H);
            g.fill(rx, ry, rx + rw, ry + CG_ROW_H, hov ? Theme.CARD_HOVER : Theme.CARD);
            if (on) g.fill(rx, ry, rx + 2, ry + CG_ROW_H, Theme.ACCENT);
            g.drawString(this.font, m.getName(), rx + 8, ry + 3, on ? Theme.TEXT : Theme.TEXT_MID, false);
            g.drawString(this.font, m.getDescription(), rx + 8, ry + 13, Theme.TEXT_FAINT, false);

            int tx = rx + rw - 26, ty = ry + 7;
            g.fill(tx, ty, tx + 18, ty + 10, on ? Theme.ACCENT : Theme.TOGGLE_OFF);
            int kx = on ? tx + 10 : tx + 2;
            g.fill(kx, ty + 2, kx + 6, ty + 8, on ? Theme.TEXT : Theme.KNOB_OFF);
        }

        g.fill(x, footTop, x + CG_W, footTop + 1, Theme.BORDER);
        g.drawString(this.font, "Активно: " + ModuleManager.enabledCount(), x + 8, footTop + 6, Theme.TEXT_DIM, false);

        int px = cgPanicX(), py = cgPanicY();
        boolean panicHov = cgInside(mouseX, mouseY, px, py, CG_PANIC_W, CG_PANIC_H);
        g.fill(px, py, px + CG_PANIC_W, py + CG_PANIC_H, panicHov ? Theme.PANIC_HOVER : Theme.PANIC);
        g.drawString(this.font, "PANIC", px + (CG_PANIC_W - this.font.width("PANIC")) / 2, py + 2, Theme.TEXT, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mx = event.x(), my = event.y();
            Category[] cats = Category.values();
            for (int i = 0; i < cats.length; i++) {
                if (cgInside(mx, my, cgLeft(), cgCatY(i), CG_SIDE, CG_CAT_H)) {
                    cgSelected = cats[i];
                    return true;
                }
            }
            List<Module> mods = ModuleManager.byCategory(cgSelected);
            for (int i = 0; i < mods.size(); i++) {
                if (cgInside(mx, my, cgRowX(), cgRowY(i), cgRowW(), CG_ROW_H)) {
                    mods.get(i).toggle();
                    return true;
                }
            }
            if (cgInside(mx, my, cgPanicX(), cgPanicY(), CG_PANIC_W, CG_PANIC_H)) {
                Panic.run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == CG_KEY_RSHIFT) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
