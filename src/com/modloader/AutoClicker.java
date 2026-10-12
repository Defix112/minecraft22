package com.modloader;

import java.awt.Robot;
import java.awt.event.InputEvent;

public class AutoClicker {

    static volatile boolean enabled = false;
    private static Robot robot;
    private static Thread clickThread;
    private static final int CPS = 12;

    static void toggle() {
        enabled = !enabled;
        Agent.writeLog("AutoClicker: " + (enabled ? "ON" : "OFF"));
        if (enabled) {
            start();
        } else {
            stop();
        }
    }

    static void tick() {
        // click thread handles timing
    }

    static void disable() {
        enabled = false;
        stop();
    }

    private static void start() {
        if (clickThread != null && clickThread.isAlive()) return;
        try {
            if (robot == null) robot = new Robot();
        } catch (Exception e) {
            Agent.writeLog("AutoClicker error: " + e.getMessage());
            enabled = false;
            return;
        }
        clickThread = new Thread(() -> {
            long interval = 1000 / CPS;
            while (enabled) {
                try {
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    Thread.sleep(10);
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                    Thread.sleep(interval - 10);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {}
            }
        }, "AutoClicker");
        clickThread.setDaemon(true);
        clickThread.start();
    }

    private static void stop() {
        if (clickThread != null) {
            clickThread.interrupt();
            clickThread = null;
        }
    }
}
