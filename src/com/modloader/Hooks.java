package com.modloader;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

// Called from bytecode injected into Minecraft.
public final class Hooks {

    private static final int KEY_RIGHT_SHIFT = 344;
    private static final int GLFW_PRESS      = 1;

    public static volatile boolean active;
    static  volatile boolean panicRequested;

    private static boolean rShiftWasDown = false;

    private Hooks() {}

    public static void onTick() {
        if (!active) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (panicRequested) {
                panicRequested = false;
                Panic.run();
                return;
            }

            // Poll RIGHT SHIFT via GLFW — reliable on any MC build
            long win = mc.getWindow().getWindow();
            boolean rShiftNow = GLFW.glfwGetKey(win, KEY_RIGHT_SHIFT) == GLFW_PRESS;
            if (rShiftNow && !rShiftWasDown && mc.screen == null) {
                mc.setScreen(new ClickGuiScreen());
            }
            rShiftWasDown = rShiftNow;

            ModuleManager.tick(mc);
        } catch (Throwable t) {
            Agent.logError("onTick", t);
        }
    }

    public static void onHud(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!active) return;
        try {
            Hud.render(graphics);
        } catch (Throwable t) {
            Agent.logError("onHud", t);
        }
    }

    // Key hook kept for compatibility but GUI is opened in onTick now
    public static void onKey(long window, int action, KeyEvent event) {}
}
