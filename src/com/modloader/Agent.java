package com.modloader;

import java.lang.instrument.Instrumentation;
import java.io.File;
import java.io.FileWriter;

public class Agent {

    private static volatile boolean loaded = false;
    private static ClickGui gui;

    public static void agentmain(String args, Instrumentation inst) {
        // write proof file so user knows agent loaded
        writeProof("Agent loaded at " + System.currentTimeMillis());

        if (loaded && gui != null) {
            System.out.println("[ClickGui] Toggle");
            gui.toggle();
            return;
        }
        loaded = true;

        System.out.println("[ClickGui] ============================");
        System.out.println("[ClickGui] AGENT INJECTED OK");
        System.out.println("[ClickGui] ============================");

        new Thread(() -> {
            try {
                // small delay to let things settle
                Thread.sleep(300);

                System.out.println("[ClickGui] Creating GUI...");
                gui = new ClickGui();
                System.out.println("[ClickGui] GUI created, showing...");
                gui.show();
                System.out.println("[ClickGui] GUI visible!");

                writeProof("GUI opened OK");

                System.out.println("[ClickGui] Starting key hook...");
                KeyHook hook = new KeyHook(gui);
                hook.run();
            } catch (Throwable t) {
                System.out.println("[ClickGui] ERROR: " + t.getMessage());
                t.printStackTrace();
                writeProof("ERROR: " + t.getMessage());
            }
        }, "ClickGui").start();
    }

    private static void writeProof(String msg) {
        try {
            File f = new File(System.getProperty("java.io.tmpdir"), "clickgui_log.txt");
            FileWriter fw = new FileWriter(f, true);
            fw.write(msg + "\n");
            fw.close();
        } catch (Exception ignored) {}
    }
}
