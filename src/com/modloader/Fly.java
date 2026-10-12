package com.modloader;

public class Fly {

    static volatile boolean enabled = false;

    static void toggle(GameHook hook) {
        enabled = !enabled;
        Agent.writeLog("Fly: " + (enabled ? "ON" : "OFF"));
        if (!enabled && hook != null && hook.isReady()) {
            hook.setFlying(false);
        }
    }

    static void tick(GameHook hook) {
        if (enabled && hook != null && hook.isReady()) {
            hook.setFlying(true);
        }
    }

    static void disable(GameHook hook) {
        enabled = false;
        if (hook != null && hook.isReady()) {
            hook.setFlying(false);
        }
    }
}
