package com.modloader;

public class Speed {

    static volatile boolean enabled = false;
    private static final float FAST = 0.15f;
    private static final float NORMAL = 0.05f;

    static void toggle(GameHook hook) {
        enabled = !enabled;
        Agent.writeLog("Speed: " + (enabled ? "ON" : "OFF"));
        if (!enabled && hook != null && hook.isReady()) {
            hook.setSpeed(NORMAL);
        }
    }

    static void tick(GameHook hook) {
        if (enabled && hook != null && hook.isReady()) {
            hook.setSpeed(FAST);
        }
    }

    static void disable(GameHook hook) {
        enabled = false;
        if (hook != null && hook.isReady()) {
            hook.setSpeed(NORMAL);
        }
    }
}
