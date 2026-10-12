package com.modloader;

import java.awt.Robot;
import java.awt.event.KeyEvent;

public class Sprint {

    static volatile boolean enabled = false;
    private static Robot robot;
    private static volatile boolean keyHeld = false;

    static void toggle() {
        enabled = !enabled;
        Agent.writeLog("Sprint: " + (enabled ? "ON" : "OFF"));
        if (enabled) {
            press();
        } else {
            release();
        }
    }

    static void tick() {
        if (enabled && !keyHeld) {
            press();
        }
    }

    static void disable() {
        enabled = false;
        release();
    }

    private static void press() {
        try {
            if (robot == null) robot = new Robot();
            robot.keyPress(KeyEvent.VK_CONTROL);
            keyHeld = true;
        } catch (Exception e) {
            Agent.writeLog("Sprint error: " + e.getMessage());
        }
    }

    private static void release() {
        try {
            if (robot != null && keyHeld) {
                robot.keyRelease(KeyEvent.VK_CONTROL);
            }
        } catch (Exception e) {}
        keyHeld = false;
    }
}
