package com.modloader;

import java.lang.instrument.Instrumentation;

public class Agent {

    private static volatile boolean loaded = false;
    private static volatile Instrumentation savedInst;

    public static void agentmain(String args, Instrumentation inst) {
        savedInst = inst;

        if (loaded) {
            System.out.println("[ClickGui] Already loaded, toggling...");
            ClickGui.getInstance().toggle();
            return;
        }

        loaded = true;
        System.out.println("[ClickGui] ========================================");
        System.out.println("[ClickGui] Agent injected into Minecraft!");
        System.out.println("[ClickGui] ========================================");

        // open GUI immediately so user sees it works
        Thread guiThread = new Thread(() -> {
            try {
                Thread.sleep(300);
                System.out.println("[ClickGui] Opening ClickGui...");
                ClickGui gui = ClickGui.getInstance();
                gui.toggle(); // show immediately

                // start key listener in background
                System.out.println("[ClickGui] Starting key listener...");
                KeyHook hook = new KeyHook(gui, inst);
                hook.run(); // blocks forever
            } catch (Throwable t) {
                System.out.println("[ClickGui] FATAL ERROR:");
                t.printStackTrace(System.out);
            }
        }, "ClickGui-Main");
        guiThread.setDaemon(true);
        guiThread.start();
    }
}
