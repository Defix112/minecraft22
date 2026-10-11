package com.modloader;

import java.lang.instrument.Instrumentation;

public class Agent {

    private static boolean loaded = false;

    public static void agentmain(String args, Instrumentation inst) {
        if (loaded) {
            System.out.println("[ClickGui] Already loaded, toggling GUI...");
            ClickGui.getInstance().toggle();
            return;
        }
        loaded = true;
        System.out.println("[ClickGui] Agent injected into Minecraft!");
        System.out.println("[ClickGui] Starting key listener (Right Shift = ClickGui)...");

        Thread thread = new Thread(() -> {
            try {
                // wait a moment for things to settle
                Thread.sleep(500);
                ClickGui gui = ClickGui.getInstance();
                KeyHook hook = new KeyHook(gui);
                hook.run();
            } catch (Exception e) {
                System.out.println("[ClickGui] Error: " + e.getMessage());
                e.printStackTrace();
            }
        }, "ClickGui-Thread");
        thread.setDaemon(true);
        thread.start();
    }
}
