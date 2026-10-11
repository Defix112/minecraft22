package com.modloader;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class KeyHook implements Runnable {

    private final ClickGui gui;
    private final Instrumentation inst;
    private boolean wasPressed = false;

    private static final int GLFW_KEY_RIGHT_SHIFT = 344;
    private static final int GLFW_PRESS = 1;

    public KeyHook(ClickGui gui, Instrumentation inst) {
        this.gui = gui;
        this.inst = inst;
    }

    @Override
    public void run() {
        // Step 1: find GLFW class
        Class<?> glfwClass = findGLFWClass();
        if (glfwClass == null) {
            System.out.println("[ClickGui] GLFW class not found!");
            System.out.println("[ClickGui] Key listener NOT active.");
            System.out.println("[ClickGui] Use test.cmd [1] to toggle ClickGui.");
            return;
        }
        System.out.println("[ClickGui] Found GLFW: " + glfwClass.getName());

        // Step 2: find window handle
        long windowHandle = findWindowHandle(glfwClass);
        if (windowHandle == 0) {
            System.out.println("[ClickGui] Window handle not found!");
            System.out.println("[ClickGui] Use test.cmd [1] to toggle ClickGui.");
            return;
        }
        System.out.println("[ClickGui] Window handle: " + windowHandle);
        System.out.println("[ClickGui] Right Shift key listener ACTIVE!");

        // Step 3: poll key state
        Method glfwGetKey = findGetKeyMethod(glfwClass);
        if (glfwGetKey == null) {
            System.out.println("[ClickGui] glfwGetKey method not found!");
            return;
        }

        while (true) {
            try {
                int state = (int) glfwGetKey.invoke(null, windowHandle, GLFW_KEY_RIGHT_SHIFT);
                boolean pressed = (state == GLFW_PRESS);

                if (pressed && !wasPressed) {
                    gui.toggle();
                }
                wasPressed = pressed;

                Thread.sleep(50);
            } catch (Exception e) {
                System.out.println("[ClickGui] Key poll error: " + e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }
    }

    private Class<?> findGLFWClass() {
        // direct lookup
        try {
            return Class.forName("org.lwjgl.glfw.GLFW");
        } catch (ClassNotFoundException ignored) {}

        // scan loaded classes
        if (inst != null) {
            for (Class<?> c : inst.getAllLoadedClasses()) {
                if (c.getName().equals("org.lwjgl.glfw.GLFW")) {
                    return c;
                }
            }
        }

        // try thread context classloader
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) return cl.loadClass("org.lwjgl.glfw.GLFW");
        } catch (Exception ignored) {}

        // try all threads' classloaders
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            try {
                ClassLoader cl = t.getContextClassLoader();
                if (cl != null) {
                    return cl.loadClass("org.lwjgl.glfw.GLFW");
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private Method findGetKeyMethod(Class<?> glfwClass) {
        try {
            Method m = glfwClass.getMethod("glfwGetKey", long.class, int.class);
            m.setAccessible(true);
            return m;
        } catch (Exception e) {
            // try declared methods
            for (Method m : glfwClass.getDeclaredMethods()) {
                if (m.getParameterCount() == 2
                        && m.getReturnType() == int.class
                        && m.getParameterTypes()[0] == long.class
                        && m.getParameterTypes()[1] == int.class) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private long findWindowHandle(Class<?> glfwClass) {
        Method getKey = findGetKeyMethod(glfwClass);
        if (getKey == null) return 0;

        // Method 1: scan all loaded classes for a long field that is a valid GLFW window
        System.out.println("[ClickGui] Scanning for window handle...");

        if (inst != null) {
            for (Class<?> c : inst.getAllLoadedClasses()) {
                String name = c.getName();
                // skip obvious non-minecraft classes
                if (name.startsWith("java.") || name.startsWith("javax.")
                        || name.startsWith("sun.") || name.startsWith("jdk.")
                        || name.startsWith("com.sun.") || name.startsWith("org.lwjgl.")
                        || name.startsWith("com.modloader.")) {
                    continue;
                }

                try {
                    // look for static fields of the class's own type (singleton pattern)
                    for (Field f : c.getDeclaredFields()) {
                        if (!Modifier.isStatic(f.getModifiers())) continue;
                        if (f.getType() != c) continue;

                        f.setAccessible(true);
                        Object singleton = f.get(null);
                        if (singleton == null) continue;

                        // now search this object's fields for a Window-like object
                        long handle = searchForWindowHandle(singleton, getKey, 0);
                        if (handle != 0) return handle;
                    }
                } catch (Throwable ignored) {}
            }
        }

        // Method 2: brute force — try recently seen window handles
        // GLFW window handles on Windows are typically pointer values
        System.out.println("[ClickGui] Trying alternative window search...");

        // try to find via thread stacks — look for render thread
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            String tname = t.getName().toLowerCase();
            if (tname.contains("render") || tname.contains("main") || tname.contains("client")) {
                try {
                    ClassLoader cl = t.getContextClassLoader();
                    if (cl == null) continue;

                    // try loading Minecraft class from this classloader
                    String[] mcClasses = {
                        "net.minecraft.client.MinecraftClient",
                        "net.minecraft.client.Minecraft",
                    };
                    for (String mcName : mcClasses) {
                        try {
                            Class<?> mcClass = cl.loadClass(mcName);
                            long handle = findHandleInClass(mcClass, getKey);
                            if (handle != 0) return handle;
                        } catch (ClassNotFoundException ignored) {}
                    }

                    // try any class with a singleton that holds a Window
                    // scan obfuscated names
                    if (inst != null) {
                        for (Class<?> c : inst.getAllLoadedClasses()) {
                            if (c.getClassLoader() != cl) continue;
                            long handle = findHandleInClass(c, getKey);
                            if (handle != 0) return handle;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        return 0;
    }

    private long searchForWindowHandle(Object obj, Method getKey, int depth) {
        if (depth > 3 || obj == null) return 0;

        try {
            for (Field f : obj.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);

                if (f.getType() == long.class) {
                    long val = f.getLong(obj);
                    if (val > 0 && testWindowHandle(val, getKey)) {
                        return val;
                    }
                } else if (!f.getType().isPrimitive() && !f.getType().isArray()
                        && !f.getType().getName().startsWith("java.")) {
                    Object child = f.get(obj);
                    if (child != null) {
                        long handle = searchForWindowHandle(child, getKey, depth + 1);
                        if (handle != 0) return handle;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private long findHandleInClass(Class<?> c, Method getKey) {
        try {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType() != c) continue;

                f.setAccessible(true);
                Object singleton = f.get(null);
                if (singleton == null) continue;

                return searchForWindowHandle(singleton, getKey, 0);
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private boolean testWindowHandle(long handle, Method getKey) {
        try {
            // if this doesn't throw, it's likely a valid GLFW window
            Object result = getKey.invoke(null, handle, GLFW_KEY_RIGHT_SHIFT);
            return result instanceof Integer;
        } catch (Throwable e) {
            return false;
        }
    }
}
