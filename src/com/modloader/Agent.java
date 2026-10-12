package com.modloader;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.io.*;
import java.nio.file.*;
import java.util.jar.JarFile;

public class Agent {

    private static volatile boolean loaded = false;
    private static volatile boolean running = true;
    private static GameHook hook;

    public static void agentmain(String args, Instrumentation inst) {
        writeLog("Agent loaded at " + System.currentTimeMillis());

        if (loaded) {
            writeLog("Already loaded, ignoring re-inject");
            return;
        }
        loaded = true;

        System.out.println("[Agent] ============================");
        System.out.println("[Agent] INJECTED OK - In-game GUI");
        System.out.println("[Agent] ============================");

        new Thread(() -> {
            try {
                if (args != null && !args.isEmpty()) {
                    File jarFile = new File(args);
                    if (jarFile.exists()) {
                        inst.appendToBootstrapClassLoaderSearch(new JarFile(jarFile));
                        writeLog("Added to bootstrap classloader: " + jarFile.getName());
                    }
                }

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
                } else {
                    writeLog("GameHook failed - join a world first, then rescan");
                }

                long windowHandle = findWindowHandle(inst);
                if (windowHandle == 0) {
                    writeLog("GLFW window handle not found");
                    writeStatus("ERROR: no window");
                    return;
                }
                writeLog("GLFW window handle: " + windowHandle);

                boolean glOk = Overlay.init(windowHandle);
                if (!glOk) {
                    writeLog("Overlay init failed");
                    writeStatus("ERROR: GL init");
                    return;
                }

                setupKeyCallback(windowHandle, inst);

                inst.addTransformer(new HookTransformer(), true);
                writeLog("Transformer registered");

                Class<?> windowClass = findWindowClass(inst);
                if (windowClass != null) {
                    inst.retransformClasses(windowClass);
                    writeLog("Window class retransformed OK");
                } else {
                    writeLog("Window class not found for retransform");
                }

                writeStatus("READY - RShift to open GUI");
                startModuleTick();
                startCommandListener();

            } catch (Throwable t) {
                writeLog("Init error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                writeStatus("ERROR");
            }
        }, "Agent-Init").start();
    }

    private static long findWindowHandle(Instrumentation inst) {
        try {
            Class<?> windowClass = findWindowClass(inst);
            if (windowClass == null) return 0;

            Object windowInstance = findWindowInstance(inst, windowClass);
            if (windowInstance == null) {
                writeLog("Window instance not found");
                return 0;
            }

            for (Field f : windowClass.getDeclaredFields()) {
                if (f.getType() == long.class && !Modifier.isStatic(f.getModifiers())) {
                    try {
                        f.setAccessible(true);
                        long val = f.getLong(windowInstance);
                        if (val != 0) {
                            writeLog("Window handle field: " + f.getName() + " = " + val);
                            return val;
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Throwable t) {
            writeLog("findWindowHandle error: " + t);
        }
        return 0;
    }

    private static Class<?> findWindowClass(Instrumentation inst) {
        for (Class<?> cls : inst.getAllLoadedClasses()) {
            if ("com.mojang.blaze3d.platform.Window".equals(cls.getName())) {
                return cls;
            }
        }
        return null;
    }

    private static Object findWindowInstance(Instrumentation inst, Class<?> windowClass) {
        for (Class<?> cls : inst.getAllLoadedClasses()) {
            String n = cls.getName();
            if (n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("sun.")
                || n.startsWith("com.sun.") || n.startsWith("jdk.") || n.startsWith("org.")
                || n.startsWith("com.google") || n.startsWith("io.") || n.startsWith("it.")
                || n.contains("$")) continue;

            Field[] fields = cls.getDeclaredFields();
            if (fields.length < 35) continue;

            for (Field f : fields) {
                if (f.getType() == cls && Modifier.isStatic(f.getModifiers())) {
                    try {
                        f.setAccessible(true);
                        Object singleton = f.get(null);
                        if (singleton == null) continue;

                        for (Field wf : cls.getDeclaredFields()) {
                            if (wf.getType() == windowClass && !Modifier.isStatic(wf.getModifiers())) {
                                wf.setAccessible(true);
                                Object win = wf.get(singleton);
                                if (win != null) {
                                    writeLog("Found Window in " + cls.getName() + "." + wf.getName());
                                    return win;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }

    private static void setupKeyCallback(long windowHandle, Instrumentation inst) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = ClassLoader.getSystemClassLoader();

            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW", true, cl);
            Class<?> callbackI = Class.forName("org.lwjgl.glfw.GLFWKeyCallbackI", true, cl);

            Method setCallback = glfw.getMethod("glfwSetKeyCallback", long.class, callbackI);

            Object oldCallback = setCallback.invoke(null, windowHandle, (Object) null);
            writeLog("Old key callback: " + (oldCallback == null ? "null" : oldCallback.getClass().getName()));

            Object proxy = Proxy.newProxyInstance(cl, new Class<?>[]{callbackI},
                (p, method, methodArgs) -> {
                    if ("invoke".equals(method.getName()) && methodArgs != null && methodArgs.length == 5) {
                        long win = (Long) methodArgs[0];
                        int key = (Integer) methodArgs[1];
                        int scancode = (Integer) methodArgs[2];
                        int action = (Integer) methodArgs[3];
                        int mods = (Integer) methodArgs[4];

                        Overlay.onKey(key, action);

                        if (Overlay.visible) {
                            return null;
                        }

                        if (oldCallback != null) {
                            try {
                                Method invoke = oldCallback.getClass().getMethod("invoke",
                                    long.class, int.class, int.class, int.class, int.class);
                                invoke.invoke(oldCallback, win, key, scancode, action, mods);
                            } catch (Exception ignored) {}
                        }
                    }
                    return null;
                });

            setCallback.invoke(null, windowHandle, proxy);
            writeLog("Key callback installed");

        } catch (Throwable t) {
            writeLog("Key callback error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static void onModuleToggle(int cat, int mod) {
        if (cat == 0 && mod == 0) Fly.toggle(hook);
        else if (cat == 0 && mod == 1) Speed.toggle(hook);
        else if (cat == 0 && mod == 2) Sprint.toggle();
        else if (cat == 1 && mod == 0) AutoClicker.toggle();
        else if (cat == 2 && mod == 0) Fullbright.toggle();
        updateStatus();
    }

    private static void startModuleTick() {
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(50);
                    if (hook != null) hook.refreshPlayer();

                    Fly.tick(hook);
                    Speed.tick(hook);
                    Sprint.tick();
                    AutoClicker.tick();
                    Fullbright.tick();
                } catch (Exception e) {}
            }
        }, "Agent-ModuleTick");
        t.setDaemon(true);
        t.start();
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
                    if ("0".equals(cmd)) {
                        doPanic();
                    } else if ("9".equals(cmd)) {
                        if (hook != null) {
                            boolean ok = hook.scan();
                            writeLog("Rescan: " + (ok ? "OK" : "FAIL"));
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {}
            }
        }, "Agent-CmdListener");
        t.setDaemon(true);
        t.start();
    }

    private static void doPanic() {
        writeLog("PANIC activated");
        running = false;

        Fly.disable(hook);
        Speed.disable(hook);
        Sprint.disable();
        AutoClicker.disable();
        Fullbright.disable();
        Overlay.visible = false;

        if (hook != null && hook.isReady()) {
            hook.restore();
        }

        String tmp = System.getProperty("java.io.tmpdir");
        new File(tmp, "clickgui_cmd.txt").delete();
        new File(tmp, "clickgui_log.txt").delete();
        new File(tmp, "clickgui_status.txt").delete();
        new File(tmp, "clickgui_pause.txt").delete();

        writeStatus("PANIC_DONE");
        System.out.println("[Agent] PANIC - all disabled and cleaned");
    }

    private static void updateStatus() {
        StringBuilder sb = new StringBuilder();
        if (Fly.enabled) sb.append("Fly:ON ");
        if (Speed.enabled) sb.append("Speed:ON ");
        if (Sprint.enabled) sb.append("Sprint:ON ");
        if (AutoClicker.enabled) sb.append("AutoClick:ON ");
        if (Fullbright.enabled) sb.append("Fullbright:ON ");
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
