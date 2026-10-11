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
                Thread.sleep(500);

                // force AWT init
                System.out.println("[ClickGui] Init AWT...");
                System.setProperty("java.awt.headless", "false");
                writeProof("AWT init OK");

                System.out.println("[ClickGui] Creating GUI...");
                gui = new ClickGui();
                writeProof("GUI created");

                System.out.println("[ClickGui] Showing GUI...");
                gui.show();
                writeProof("GUI shown OK");

                System.out.println("[ClickGui] Starting key hook...");
                KeyHook hook = new KeyHook(gui);
                hook.run();
            } catch (Throwable t) {
                String err = t.getClass().getName() + ": " + t.getMessage();
                System.out.println("[ClickGui] ERROR: " + err);
                t.printStackTrace();
                writeProof("ERROR: " + err);

                // stacktrace to proof file
                try {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    t.printStackTrace(new java.io.PrintWriter(sw));
                    writeProof(sw.toString());
                } catch (Exception ignored) {}
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
