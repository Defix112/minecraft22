package com.modloader;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

final class Hud {

    private Hud() {}

    static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen instanceof ClickGuiScreen) return;
        Font font = mc.font;

        String brand = "System";
        String suffix = " Client";
        int w = font.width(brand) + font.width(suffix) + 10;
        g.fill(4, 4, 4 + w, 18, Theme.HUD_BG);
        g.fill(4, 4, 4 + w, 5, Theme.ACCENT);
        g.drawString(font, brand, 9, 7, Theme.ACCENT, true);
        g.drawString(font, suffix, 9 + font.width(brand), 7, Theme.TEXT, true);

        List<String> names = new ArrayList<>();
        for (Module m : ModuleManager.all()) if (m.isEnabled()) names.add(m.getName());
        names.sort((a, b) -> font.width(b) - font.width(a));

        int right = g.guiWidth();
        int y = 4;
        for (String name : names) {
            int x = right - font.width(name) - 8;
            g.fill(x - 3, y, right, y + 12, Theme.HUD_BG);
            g.fill(right - 2, y, right, y + 12, Theme.ACCENT);
            g.drawString(font, name, x, y + 2, Theme.TEXT, true);
            y += 12;
        }
    }
}
