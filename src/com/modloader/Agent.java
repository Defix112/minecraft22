package com.modloader;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Agent {

    private static final File TMP = new File(System.getProperty("java.io.tmpdir"));
    private static final File LOG = new File(TMP, "clickgui_log.txt");
    private static final File STATUS = new File(TMP, "clickgui_status.txt");
    private static final File CMD = new File(TMP, "clickgui_cmd.txt");

    private static final Set<String> reportedErrors = ConcurrentHashMap.newKeySet();
    private static final List<Class<?>> hookedClasses = new ArrayList<>();

    private static volatile boolean loaded;
    private static Instrumentation inst;
    private static HookTransformer transformer;

    public static void agentmain(String args, Instrumentation instrumentation) {
        if (loaded) {
            log("Already injected");
            return;
        }
        loaded = true;
        inst = instrumentation;
        log("Agent loaded");
        try {
            transformer = new HookTransformer();
            inst.addTransformer(transformer, true);

            Set<String> wanted = HookTransformer.targetClassNames();
            for (Class<?> c : inst.getAllLoadedClasses()) {
                if (wanted.contains(c.getName())) hookedClasses.add(c);
            }
            log("Target classes found: " + hookedClasses.size() + "/" + wanted.size());
            inst.retransformClasses(hookedClasses.toArray(new Class<?>[0]));

            Hooks.active = true;
            writeStatus("READY - press RIGHT SHIFT in game");
            log("READY - press RIGHT SHIFT in game");
            startCommandListener();
        } catch (Throwable t) {
            logError("init", t);
            writeStatus("ERROR - see log");
        }
    }

    // Retransforming without our transformer restores the original game bytecode.
    static void unhook() {
        try {
            inst.removeTransformer(transformer);
            inst.retransformClasses(hookedClasses.toArray(new Class<?>[0]));
            log("Hooks removed, game code restored");
        } catch (Throwable t) {
            logError("unhook", t);
        }
    }

    private static void startCommandListener() {
        Thread t = new Thread(() -> {
            while (Hooks.active) {
                try {
                    Thread.sleep(200);
                    if (!CMD.exists()) continue;
                    String cmd = new String(Files.readAllBytes(CMD.toPath())).trim();
                    CMD.delete();
                    log("CMD: " + cmd);
                    if ("0".equals(cmd)) Hooks.panicRequested = true;
                } catch (InterruptedException e) {
                    return;
                } catch (Exception ignored) {
                }
            }
        }, "SystemClient-Cmd");
        t.setDaemon(true);
        t.start();
    }

    static void writeStatus(String status) {
        try (FileWriter w = new FileWriter(STATUS, false)) {
            w.write(status);
        } catch (Exception ignored) {
        }
    }

    static void log(String msg) {
        System.out.println("[SystemClient] " + msg);
        try (FileWriter w = new FileWriter(LOG, true)) {
            w.write(msg + "\n");
        } catch (Exception ignored) {
        }
    }

    static void logError(String where, Throwable t) {
        if (!reportedErrors.add(where + ":" + t)) return;
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        log("ERROR in " + where + ": " + sw);
    }
}
