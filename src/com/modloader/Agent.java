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
                gui.toggle();
                return;
            }
            if (guiProcess != null && guiProcess.isAlive()) {
                System.out.println("[ClickGui] Already running. Use Right Shift.");
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
                if (tryInProcess(inst)) return;
                writeProof("Trying separate process fallback...");
                tryExternalProcess(args);
            } catch (Throwable t) {
                logError("Init", t);
            }
        }, "ClickGui-Init").start();
    }

    private static boolean tryInProcess(Instrumentation inst) {
        try {
            writeProof("Attempting in-process GUI...");

            try {
                java.lang.Module javaDesktop = java.awt.GraphicsEnvironment.class.getModule();
                inst.redefineModule(javaDesktop,
                        Set.of(), Map.of(),
                        Map.of("java.awt", Set.of(Agent.class.getModule())),
                        Set.of(), Map.of());
            } catch (Throwable t) {
                writeProof("Module open: " + t.getMessage());
            }

            System.setProperty("java.awt.headless", "false");
            try {
                Field f = java.awt.GraphicsEnvironment.class.getDeclaredField("headless");
                f.setAccessible(true);
                f.set(null, null);
            } catch (Throwable t) {
                writeProof("Headless reset: " + t.getMessage());
            }

            try {
                java.awt.Toolkit.getDefaultToolkit();
            } catch (Throwable t) {
                writeProof("Toolkit failed: " + t.getMessage());
                return false;
            }

            ModuleManager mgr = new ModuleManager();
            gui = new ClickGui(mgr);
            gui.show();
            writeProof("In-process GUI OK");

            System.out.println("[ClickGui] Right Shift = toggle, ESC = close");
            new KeyHook(gui).run();
            return true;
        } catch (Throwable t) {
            logError("In-process", t);
            gui = null;
            return false;
        }
    }

    private static void tryExternalProcess(String jarPath) {
        try {
            if (jarPath == null || jarPath.isEmpty()) {
                writeProof("No jar path for external process");
                return;
            }
            File jarFile = new File(jarPath);
            if (!jarFile.exists()) {
                writeProof("Jar not found: " + jarPath);
                return;
            }

            String javaBin = findJava();
            writeProof("External Java: " + javaBin);

            ProcessBuilder pb = new ProcessBuilder(
                javaBin, "-cp", jarFile.getAbsolutePath(),
                "com.modloader.ClickGuiApp"
            );
            pb.redirectErrorStream(true);
            guiProcess = pb.start();
            writeProof("External GUI PID: " + guiProcess.pid());

            BufferedReader br = new BufferedReader(new InputStreamReader(guiProcess.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
                writeProof("[EXT] " + line);
            }
            guiProcess.waitFor();
        } catch (Throwable t) {
            logError("External", t);
        }
    }

    private static String findJava() {
        String home = System.getProperty("java.home");
        for (String name : new String[]{"javaw.exe", "java.exe", "java"}) {
            File f = new File(home, "bin" + File.separator + name);
            if (f.exists()) return f.getAbsolutePath();
        }
        return "javaw.exe";
    }

    private static void logError(String ctx, Throwable t) {
        String msg = t.getClass().getName() + ": " + t.getMessage();
        System.out.println("[ClickGui] " + ctx + " ERROR: " + msg);
        t.printStackTrace();
        writeProof(ctx + " ERROR: " + msg);
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
