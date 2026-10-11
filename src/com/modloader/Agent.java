package com.modloader;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;

public class Agent {

    private static volatile boolean loaded = false;
    private static ClickGui gui;
    private static Process guiProcess;

    public static void agentmain(String args, Instrumentation inst) {
        writeProof("Agent loaded at " + System.currentTimeMillis());

        if (loaded) {
            if (gui != null) {
                System.out.println("[ClickGui] Toggle (in-process)");
                gui.toggle();
                return;
            }
            if (guiProcess != null && guiProcess.isAlive()) {
                System.out.println("[ClickGui] GUI process running. Use Right Shift.");
                return;
            }
        }
        loaded = true;

        System.out.println("[ClickGui] ============================");
        System.out.println("[ClickGui] AGENT INJECTED OK");
        System.out.println("[ClickGui] ============================");

        new Thread(() -> {
            try {
                Thread.sleep(300);

                // Try in-process GUI (truly injected into game)
                if (tryInProcess(inst)) return;

                // Fallback: separate process
                writeProof("Trying separate process fallback...");
                tryExternalProcess(args);

            } catch (Throwable t) {
                logError("Init thread", t);
            }
        }, "ClickGui-Init").start();
    }

    private static boolean tryInProcess(Instrumentation inst) {
        try {
            writeProof("Attempting in-process GUI...");

            // Open java.desktop module for headless reset
            try {
                Module javaDesktop = java.awt.GraphicsEnvironment.class.getModule();
                Module ours = Agent.class.getModule();
                inst.redefineModule(javaDesktop,
                        Set.of(), Map.of(),
                        Map.of("java.awt", Set.of(ours)),
                        Set.of(), Map.of());
                writeProof("Module opened OK");
            } catch (Throwable t) {
                writeProof("Module open failed (non-fatal): " + t.getMessage());
            }

            // Force non-headless
            System.setProperty("java.awt.headless", "false");
            try {
                Field f = java.awt.GraphicsEnvironment.class.getDeclaredField("headless");
                f.setAccessible(true);
                f.set(null, null);
                writeProof("Headless reset OK");
            } catch (Throwable t) {
                writeProof("Headless reset failed (non-fatal): " + t.getMessage());
            }

            // Initialize AWT toolkit
            try {
                java.awt.Toolkit tk = java.awt.Toolkit.getDefaultToolkit();
                writeProof("Toolkit: " + tk.getClass().getName());
            } catch (Throwable t) {
                writeProof("Toolkit init failed: " + t.getMessage());
                return false;
            }

            System.out.println("[ClickGui] Creating overlay...");
            gui = new ClickGui();
            writeProof("GUI created OK");

            System.out.println("[ClickGui] Showing overlay...");
            gui.show();
            writeProof("GUI shown OK");

            System.out.println("[ClickGui] Starting key hook...");
            System.out.println("[ClickGui] Right Shift = toggle, ESC = close");
            KeyHook hook = new KeyHook(gui);
            hook.run();
            return true;
        } catch (Throwable t) {
            logError("In-process GUI", t);
            gui = null;
            return false;
        }
    }

    private static void tryExternalProcess(String jarPath) {
        try {
            if (jarPath == null || jarPath.isEmpty()) {
                writeProof("ERROR: jar path not provided for external process");
                return;
            }

            File jarFile = new File(jarPath);
            if (!jarFile.exists()) {
                writeProof("ERROR: jar not found at " + jarPath);
                return;
            }

            String javaBin = findJava();
            writeProof("External Java: " + javaBin);

            System.out.println("[ClickGui] Launching external GUI...");
            ProcessBuilder pb = new ProcessBuilder(
                javaBin, "-cp", jarFile.getAbsolutePath(),
                "com.modloader.ClickGuiApp"
            );
            pb.redirectErrorStream(true);
            guiProcess = pb.start();
            writeProof("External GUI PID: " + guiProcess.pid());

            System.out.println("[ClickGui] External GUI PID: " + guiProcess.pid());
            System.out.println("[ClickGui] Right Shift = toggle, ESC = close");

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(guiProcess.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
                writeProof("[EXT] " + line);
            }

            int exit = guiProcess.waitFor();
            writeProof("External GUI exited: " + exit);
        } catch (Throwable t) {
            logError("External process", t);
        }
    }

    private static String findJava() {
        String home = System.getProperty("java.home");
        for (String name : new String[]{"javaw.exe", "java.exe", "java"}) {
            File f = new File(home, "bin" + File.separator + name);
            if (f.exists()) return f.getAbsolutePath();
        }
        writeProof("WARNING: java not found in " + home);
        return "javaw.exe";
    }

    private static void logError(String context, Throwable t) {
        String msg = t.getClass().getName() + ": " + t.getMessage();
        System.out.println("[ClickGui] " + context + " ERROR: " + msg);
        t.printStackTrace();
        writeProof(context + " ERROR: " + msg);
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            writeProof(sw.toString());
        } catch (Exception ignored) {}
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
