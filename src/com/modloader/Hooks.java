package com.modloader;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

// Called from bytecode injected into Minecraft. Each method's parameters must match the hooked game method exactly (checked by build.sh).
public final class Hooks {

    private static final int KEY_RIGHT_SHIFT = GLFW.GLFW_KEY_RIGHT_SHIFT;
    private static final int GLFW_PRESS      = GLFW.GLFW_PRESS;

    // System property is visible across all classloaders — avoids split-identity bug
    private static final String PROP = "sc.active";

    public static volatile boolean active;
    static volatile boolean panicRequested;

    // Edge-detect RIGHT SHIFT via GLFW poll in onTick — no onKey hook needed
    private static boolean shiftWasDown;

    private Hooks() {}

    private static boolean isActive() {
        return active || "1".equals(System.getProperty(PROP));
    }

    public static void onTick() {
        if (!isActive()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (panicRequested || GameState.panicRequested) {
                panicRequested = false;
                GameState.panicRequested = false;
                Panic.run();
                return;
            }
            // Poll RIGHT SHIFT directly — reliable across all classloaders
            long win = GLFW.glfwGetCurrentContext();
            boolean shiftDown = GLFW.glfwGetKey(win, KEY_RIGHT_SHIFT) == GLFW_PRESS;
            if (shiftDown && !shiftWasDown && mc.screen == null) {
                mc.setScreen(new ClickGuiScreen());
            }
            shiftWasDown = shiftDown;
            ModuleManager.tick(mc);
            // Update state for C++ overlay
            net.minecraft.world.entity.player.Player p = mc.player;
            if (p != null) {
                GameState.playerX = p.getX();
                GameState.playerY = p.getY();
                GameState.playerZ = p.getZ();
            }
            GameState.moduleStates = ModuleManager.moduleStates();
            GameState.active = true;
            // Handle toggle request from C++ overlay
            String tog = GameState.pendingToggle;
            if (!tog.isEmpty()) {
                GameState.pendingToggle = "";
                ModuleManager.toggle(tog);
            }
        } catch (Throwable t) {
            Agent.logError("onTick", t);
        }
    }

    public static void onHud(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!isActive()) return;
        try {
            Hud.render(graphics);
        } catch (Throwable t) {
            Agent.logError("onHud", t);
        }
    }
}
