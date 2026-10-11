package com.modloader;

import java.lang.instrument.Instrumentation;

public class Agent {

    private static volatile boolean loaded = false;

    public static void agentmain(String args, Instrumentation inst) {
        if (loaded) {
            System.out.println("[ClickGui] Re-inject: toggling GUI");
            ClickGui gui = ClickGui.getInstance();
            if (gui != null) gui.toggle();
            return;
        }
        loaded = true;

        System.out.println("[ClickGui] ============================");
        System.out.println("[ClickGui] Injected successfully!");
        System.out.println("[ClickGui] ============================");

        new Thread(() -> {
            try {
                Thread.sleep(200);
                ClickGui gui = ClickGui.getInstance();
                gui.show();
                System.out.println("[ClickGui] GUI opened!");
                System.out.println("[ClickGui] Right Shift = toggle");

                new KeyHook(gui).run();
            } catch (Throwable t) {
                System.out.println("[ClickGui] ERROR: " + t);
                t.printStackTrace(System.out);
            }
        }, "ClickGui").start();
    }
}
