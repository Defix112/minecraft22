package com.modloader;

import java.lang.instrument.Instrumentation;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Agent {

    private static volatile boolean loaded = false;
    private static Process guiProcess;

    public static void agentmain(String args, Instrumentation inst) {
        writeProof("Agent loaded at " + System.currentTimeMillis());

        if (loaded && guiProcess != null && guiProcess.isAlive()) {
            writeProof("GUI process already running (PID " + guiProcess.pid() + ")");
            System.out.println("[ClickGui] Already running. Use Right Shift to toggle.");
            return;
        }
        loaded = true;

        System.out.println("[ClickGui] ============================");
        System.out.println("[ClickGui] AGENT INJECTED OK");
        System.out.println("[ClickGui] ============================");

        String jarPath = args;
        if (jarPath == null || jarPath.isEmpty()) {
            writeProof("ERROR: jar path not provided");
            System.out.println("[ClickGui] ERROR: jar path missing");
            return;
        }

        writeProof("Jar path: " + jarPath);

        new Thread(() -> {
            try {
                Thread.sleep(300);

                String javaBin = findJava();
                writeProof("Java binary: " + javaBin);

                File jarFile = new File(jarPath);
                if (!jarFile.exists()) {
                    writeProof("ERROR: jar not found at " + jarPath);
                    return;
                }

                System.out.println("[ClickGui] Launching GUI process...");
                ProcessBuilder pb = new ProcessBuilder(
                    javaBin,
                    "-cp", jarFile.getAbsolutePath(),
                    "com.modloader.ClickGuiApp"
                );
                pb.redirectErrorStream(true);
                guiProcess = pb.start();
                writeProof("GUI process started, PID: " + guiProcess.pid());

                System.out.println("[ClickGui] GUI process PID: " + guiProcess.pid());
                System.out.println("[ClickGui] Right Shift = toggle, ESC = close");

                BufferedReader br = new BufferedReader(
                    new InputStreamReader(guiProcess.getInputStream()));
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                    writeProof("[GUI] " + line);
                }

                int exit = guiProcess.waitFor();
                writeProof("GUI process exited with code: " + exit);

            } catch (Throwable t) {
                String err = t.getClass().getName() + ": " + t.getMessage();
                System.out.println("[ClickGui] ERROR: " + err);
                t.printStackTrace();
                writeProof("ERROR: " + err);
                try {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    t.printStackTrace(new java.io.PrintWriter(sw));
                    writeProof(sw.toString());
                } catch (Exception ignored) {}
            }
        }, "ClickGui-Launcher").start();
    }

    private static String findJava() {
        String javaHome = System.getProperty("java.home");

        // try javaw.exe first (no console window)
        File javaw = new File(javaHome, "bin" + File.separator + "javaw.exe");
        if (javaw.exists()) return javaw.getAbsolutePath();

        // try java.exe
        File java = new File(javaHome, "bin" + File.separator + "java.exe");
        if (java.exists()) return java.getAbsolutePath();

        // try java (Linux/Mac)
        File javaUnix = new File(javaHome, "bin" + File.separator + "java");
        if (javaUnix.exists()) return javaUnix.getAbsolutePath();

        writeProof("WARNING: could not find java in " + javaHome + ", trying PATH");
        return "javaw.exe";
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
