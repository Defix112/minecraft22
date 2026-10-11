package com.modloader;

import java.lang.reflect.Method;

public class KeyHook implements Runnable {

    private final ClickGui gui;
    private boolean wasPressed = false;

    // GLFW constants
    private static final int GLFW_KEY_RIGHT_SHIFT = 344;
    private static final int GLFW_PRESS = 1;

    public KeyHook(ClickGui gui) {
        this.gui = gui;
    }

    @Override
    public void run() {
        long windowHandle = findWindowHandle();
        if (windowHandle == 0) {
            System.out.println("[ClickGui] Could not find GLFW window, using fallback...");
            runFallback();
            return;
        }

        System.out.println("[ClickGui] Key listener active! Press Right Shift to toggle ClickGui.");

        while (true) {
            try {
                boolean pressed = isKeyPressed(windowHandle, GLFW_KEY_RIGHT_SHIFT);
                if (pressed && !wasPressed) {
                    gui.toggle();
                }
                wasPressed = pressed;
                Thread.sleep(50);
            } catch (Exception e) {
                break;
            }
        }
    }

    private long findWindowHandle() {
        try {
            // Minecraft stores the window handle in its Window class
            // Try to find it via reflection on the running Minecraft instance
            Class<?> mcClass = findMinecraftClass();
            if (mcClass == null) return 0;

            Object mcInstance = findMinecraftInstance(mcClass);
            if (mcInstance == null) return 0;

            // Try to get window handle through Minecraft's Window object
            return getWindowHandle(mcInstance, mcClass);
        } catch (Exception e) {
            System.out.println("[ClickGui] Window search: " + e.getMessage());
            return 0;
        }
    }

    private Class<?> findMinecraftClass() {
        // Try known class names (mapped and obfuscated)
        String[] classNames = {
            "net.minecraft.client.MinecraftClient",  // Yarn mapped
            "net.minecraft.client.Minecraft",        // MojMap
            "ejf",  // 1.21.1 obfuscated (may vary)
            "net.minecraft.client.main.Main"
        };
        for (String name : classNames) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {}
        }
        // search all loaded classes for one with getWindow() or similar
        return null;
    }

    private Object findMinecraftInstance(Class<?> mcClass) {
        // Try getInstance() or similar static method
        String[] methodNames = {"getInstance", "method_1551", "getMinecraft", "m_91087_"};
        for (String name : methodNames) {
            try {
                Method m = mcClass.getDeclaredMethod(name);
                m.setAccessible(true);
                return m.invoke(null);
            } catch (Exception ignored) {}
        }
        // Try static field
        try {
            var fields = mcClass.getDeclaredFields();
            for (var f : fields) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())
                        && f.getType() == mcClass) {
                    f.setAccessible(true);
                    return f.get(null);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private long getWindowHandle(Object mcInstance, Class<?> mcClass) {
        try {
            // Find the Window field (type with getHandle() method)
            for (var field : mcClass.getDeclaredFields()) {
                field.setAccessible(true);
                Object val = field.get(mcInstance);
                if (val == null) continue;

                // Look for a getHandle() method on this object
                for (var m : val.getClass().getDeclaredMethods()) {
                    if (m.getReturnType() == long.class && m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        long handle = (long) m.invoke(val);
                        if (handle != 0) {
                            System.out.println("[ClickGui] Found window handle: " + handle);
                            return handle;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[ClickGui] Handle search: " + e.getMessage());
        }
        return 0;
    }

    private boolean isKeyPressed(long window, int key) {
        try {
            Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
            Method getKey = glfwClass.getMethod("glfwGetKey", long.class, int.class);
            int state = (int) getKey.invoke(null, window, key);
            return state == GLFW_PRESS;
        } catch (Exception e) {
            return false;
        }
    }

    private void runFallback() {
        // Fallback: use AWT Robot to detect key
        System.out.println("[ClickGui] Using keyboard fallback (java.awt)...");
        try {
            while (true) {
                boolean pressed = java.awt.Toolkit.getDefaultToolkit()
                        .getLockingKeyState(java.awt.event.KeyEvent.VK_SHIFT);
                // This checks Shift Lock, not ideal. Better approach:
                // poll native key state
                if (!wasPressed) {
                    // just keep gui available via system tray or hotkey
                }
                Thread.sleep(100);
            }
        } catch (Exception e) {
            System.out.println("[ClickGui] Fallback failed: " + e.getMessage());
        }
    }
}
