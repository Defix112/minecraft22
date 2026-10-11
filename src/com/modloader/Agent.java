package com.modloader;

import java.lang.instrument.Instrumentation;

public class Agent {

    public static void premain(String args, Instrumentation inst) {
        init();
    }

    public static void agentmain(String args, Instrumentation inst) {
        init();
    }

    private static void init() {
        System.out.println("[ModLoader] Агент загружен в Minecraft!");
        Thread guiThread = new Thread(() -> {
            try {
                KeyListener listener = new KeyListener();
                listener.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "ModLoader-KeyListener");
        guiThread.setDaemon(true);
        guiThread.start();
    }
}
