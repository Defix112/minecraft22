package com.modloader;

import java.lang.instrument.Instrumentation;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Agent {

    private static volatile boolean loaded = false;
    private static volatile boolean running = true;
    private static GameHook hook;

    private static volatile boolean flyEnabled = false;
    private static volatile boolean speedEnabled = false;
    private static volatile boolean sprintEnabled = false;
    private static volatile boolean autoClickEnabled = false;
    private static volatile boolean fullbrightEnabled = false;

    private static Process sprintProcess;
    private static Process autoClickProcess;

    public static void agentmain(String args, Instrumentation inst) {
        writeLog("Agent loaded at " + System.currentTimeMillis());

        if (loaded) {
            writeLog("Already loaded, ignoring re-inject");
            return;
        }
        loaded = true;

        System.out.println("[Agent] ============================");
        System.out.println("[Agent] INJECTED OK - CMD control");
        System.out.println("[Agent] ============================");

        new Thread(() -> {
            try {
                hook = new GameHook(inst);
                writeLog("GameHook created, scanning...");

                boolean found = hook.scan();
                if (!found) {
                    writeLog("Scan 1 failed, retry in 3s...");
                    Thread.sleep(3000);
                    found = hook.scan();
                }
                if (!found) {
                    writeLog("Scan 2 failed, retry in 5s...");
                    Thread.sleep(5000);
                    found = hook.scan();
                }

                if (found) {
                    writeLog("GameHook READY");
                    writeStatus("READY");
                } else {
                    writeLog("GameHook failed - join a world first, then press [9] Rescan");
                    writeStatus("NEED_WORLD");
                }

                startCommandListener();
                startModuleTick();

            } catch (Throwable t) {
                writeLog("Init error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                writeStatus("ERROR");
            }
        }, "Agent-Init").start();
    }

    private static void startCommandListener() {
        Thread t = new Thread(() -> {
            File cmdFile = new File(System.getProperty("java.io.tmpdir"), "clickgui_cmd.txt");
            while (running) {
                try {
                    Thread.sleep(200);
                    if (!cmdFile.exists()) continue;

                    String cmd = new String(Files.readAllBytes(cmdFile.toPath())).trim();
                    cmdFile.delete();
                    if (cmd.isEmpty()) continue;

                    writeLog("CMD received: " + cmd);
                    processCommand(cmd);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    // ignore
                }
            }
        }, "Agent-CmdListener");
        t.setDaemon(true);
        t.start();
    }

    private static void processCommand(String cmd) {
        switch (cmd) {
            case "0":
                doPanic();
                break;
            case "1":
                flyEnabled = !flyEnabled;
                writeLog("Fly: " + (flyEnabled ? "ON" : "OFF"));
                if (!flyEnabled && hook != null && hook.isReady()) {
                    hook.setFlying(false);
                }
                updateStatus();
                break;
            case "2":
                speedEnabled = !speedEnabled;
                writeLog("Speed: " + (speedEnabled ? "ON" : "OFF"));
                if (!speedEnabled && hook != null && hook.isReady()) {
                    hook.setSpeed(0.05f);
                }
                updateStatus();
                break;
            case "3":
                sprintEnabled = !sprintEnabled;
                writeLog("Sprint: " + (sprintEnabled ? "ON" : "OFF"));
                if (sprintEnabled) startSprint(); else stopSprint();
                updateStatus();
                break;
            case "4":
                autoClickEnabled = !autoClickEnabled;
                writeLog("AutoClick: " + (autoClickEnabled ? "ON" : "OFF"));
                if (autoClickEnabled) startAutoClick(); else stopAutoClick();
                updateStatus();
                break;
            case "5":
                fullbrightEnabled = !fullbrightEnabled;
                writeLog("Fullbright: " + (fullbrightEnabled ? "ON" : "OFF"));
                setGamma(fullbrightEnabled ? 16.0 : 1.0);
                updateStatus();
                break;
            case "9":
                if (hook != null) {
                    boolean ok = hook.scan();
                    writeLog("Rescan: " + (ok ? "OK" : "FAIL"));
                    writeStatus(ok ? "READY" : "NEED_WORLD");
                }
                break;
            default:
                writeLog("Unknown: " + cmd);
        }
    }

    private static void doPanic() {
        writeLog("PANIC activated");
        running = false;

        flyEnabled = false;
        speedEnabled = false;
        autoClickEnabled = false;
        sprintEnabled = false;
        fullbrightEnabled = false;

        if (hook != null && hook.isReady()) {
            hook.setFlying(false);
            hook.setSpeed(0.05f);
            hook.restore();
        }

        stopSprint();
        stopAutoClick();
        setGamma(1.0);

        String tmp = System.getProperty("java.io.tmpdir");
        new File(tmp, "clickgui_cmd.txt").delete();
        new File(tmp, "clickgui_log.txt").delete();
        new File(tmp, "clickgui_status.txt").delete();
        new File(tmp, "clickgui_pause.txt").delete();

        writeStatus("PANIC_DONE");
        System.out.println("[Agent] PANIC - all disabled and cleaned");
    }

    private static void startModuleTick() {
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(50);
                    if (hook == null || !hook.isReady()) {
                        if (hook != null) hook.refreshPlayer();
                        continue;
                    }
                    hook.refreshPlayer();
                    if (!hook.isReady()) continue;

                    if (flyEnabled) hook.setFlying(true);
                    if (speedEnabled) hook.setSpeed(0.15f);
                } catch (Exception e) {
                    // ignore
                }
            }
        }, "Agent-ModuleTick");
        t.setDaemon(true);
        t.start();
    }

    private static void startSprint() {
        if (sprintProcess != null && sprintProcess.isAlive()) return;
        try {
            sprintProcess = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NoLogo",
                "-ExecutionPolicy", "Bypass", "-Command",
                "Add-Type -TypeDefinition @'\n" +
                "using System; using System.Runtime.InteropServices;\n" +
                "public class K { [DllImport(\"user32.dll\")] public static extern void keybd_event(byte v,byte s,int f,int e); }\n" +
                "'@\n" +
                "while($true) { [K]::keybd_event(0xA2,0,0,0); Start-Sleep -Milliseconds 50 }"
            ).redirectErrorStream(true).start();
        } catch (Exception e) {
            writeLog("Sprint error: " + e.getMessage());
        }
    }

    private static void stopSprint() {
        if (sprintProcess != null) {
            sprintProcess.destroyForcibly();
            sprintProcess = null;
        }
        try {
            new ProcessBuilder("powershell.exe", "-NoProfile", "-Command",
                "Add-Type -TypeDefinition @'\nusing System; using System.Runtime.InteropServices;\n" +
                "public class R { [DllImport(\"user32.dll\")] public static extern void keybd_event(byte v,byte s,int f,int e); }\n" +
                "'@; [R]::keybd_event(0xA2,0,2,0)"
            ).start();
        } catch (Exception e) {}
    }

    private static void startAutoClick() {
        if (autoClickProcess != null && autoClickProcess.isAlive()) return;
        try {
            autoClickProcess = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NoLogo",
                "-ExecutionPolicy", "Bypass", "-Command",
                "Add-Type -TypeDefinition @'\n" +
                "using System; using System.Runtime.InteropServices;\n" +
                "public class M {\n" +
                "  [DllImport(\"user32.dll\")] public static extern void mouse_event(int f,int x,int y,int d,int e);\n" +
                "  [DllImport(\"user32.dll\")] public static extern short GetAsyncKeyState(int k);\n" +
                "}\n'@\n" +
                "while($true) {\n" +
                "  $held = ([M]::GetAsyncKeyState(1) -band 0x8000) -ne 0\n" +
                "  if($held) {\n" +
                "    [M]::mouse_event(2,0,0,0,0); Start-Sleep -Milliseconds 10\n" +
                "    [M]::mouse_event(4,0,0,0,0)\n" +
                "  }\n" +
                "  Start-Sleep -Milliseconds 73\n" +
                "}"
            ).redirectErrorStream(true).start();
        } catch (Exception e) {
            writeLog("AutoClick error: " + e.getMessage());
        }
    }

    private static void stopAutoClick() {
        if (autoClickProcess != null) {
            autoClickProcess.destroyForcibly();
            autoClickProcess = null;
        }
    }

    private static void setGamma(double v) {
        try {
            String ad = System.getenv("APPDATA");
            if (ad == null) return;
            File f = new File(ad, ".minecraft" + File.separator + "options.txt");
            if (!f.exists()) return;
            java.util.List<String> lines = Files.readAllLines(f.toPath());
            java.util.List<String> out = new java.util.ArrayList<>();
            boolean found = false;
            for (String l : lines) {
                if (l.startsWith("gamma:")) { out.add("gamma:" + v); found = true; }
                else out.add(l);
            }
            if (!found) out.add("gamma:" + v);
            Files.write(f.toPath(), out);
            writeLog("Gamma -> " + v);
        } catch (Exception e) {
            writeLog("Gamma error: " + e.getMessage());
        }
    }

    private static void updateStatus() {
        StringBuilder sb = new StringBuilder();
        if (flyEnabled) sb.append("Fly:ON ");
        if (speedEnabled) sb.append("Speed:ON ");
        if (sprintEnabled) sb.append("Sprint:ON ");
        if (autoClickEnabled) sb.append("AutoClick:ON ");
        if (fullbrightEnabled) sb.append("Fullbright:ON ");
        if (sb.length() == 0) sb.append("All OFF");
        writeStatus(sb.toString().trim());
    }

    private static void writeStatus(String status) {
        try {
            File f = new File(System.getProperty("java.io.tmpdir"), "clickgui_status.txt");
            FileWriter fw = new FileWriter(f, false);
            fw.write(status);
            fw.close();
        } catch (Exception ignored) {}
    }

    static void writeLog(String msg) {
        System.out.println("[Agent] " + msg);
        try {
            File f = new File(System.getProperty("java.io.tmpdir"), "clickgui_log.txt");
            FileWriter fw = new FileWriter(f, true);
            fw.write(msg + "\n");
            fw.close();
        } catch (Exception ignored) {}
    }
}
