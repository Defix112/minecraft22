package com.modloader;

public class Module {

    final String name, category, desc;
    volatile boolean enabled;
    Process process;
    Thread thread;
    Runnable onEnable, onDisable;

    Module(String name, String category, String desc) {
        this.name = name;
        this.category = category;
        this.desc = desc;
    }

    void toggle() {
        enabled = !enabled;
        try {
            if (enabled && onEnable != null) onEnable.run();
            if (!enabled) {
                if (onDisable != null) onDisable.run();
                kill();
            }
        } catch (Exception e) {
            System.out.println("[ClickGui] " + name + " error: " + e.getMessage());
            enabled = false;
        }
    }

    void kill() {
        if (process != null) { process.destroyForcibly(); process = null; }
        if (thread != null) { thread.interrupt(); thread = null; }
    }

    void startPS(String script) {
        thread = new Thread(() -> {
            try {
                process = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NoLogo",
                    "-ExecutionPolicy", "Bypass", "-Command", script
                ).redirectErrorStream(true).start();
                process.waitFor();
            } catch (Exception e) {
                System.out.println("[ClickGui] " + name + " PS failed: " + e.getMessage());
            }
        }, "Mod-" + name);
        thread.setDaemon(true);
        thread.start();
    }
}
