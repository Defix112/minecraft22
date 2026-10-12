package com.modloader;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;

// Called from bytecode injected into Minecraft. Each method's parameters must match the hooked game method exactly (checked by build.sh).
public final class Hooks {

    private static final int KEY_RIGHT_SHIFT = 344;
    private static final int GLFW_PRESS = 1;

    public static volatile boolean active;
    static volatile boolean panicRequested;
    private static volatile boolean openRequested;

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
            if (openRequested) {
                openRequested = false;
                if (mc.screen == null) mc.setScreen(new ClickGuiScreen());
            }
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

    // The screen is opened on the next tick so this key press is not also delivered to the new screen.
    public static void onKey(long window, int action, KeyEvent event) {
        if (!active) return;
        try {
            if (action == GLFW_PRESS && event.key() == KEY_RIGHT_SHIFT && Minecraft.getInstance().screen == null) {
                openRequested = true;
            }
        } catch (Throwable t) {
            Agent.logError("onKey", t);
        }
    }
}
