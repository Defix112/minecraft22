package com.modloader;

public class ClickGuiApp {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        System.out.println("[ClickGui] Starting standalone GUI...");

        ModuleManager mgr = new ModuleManager();
        ClickGui gui = new ClickGui(mgr);
        gui.show();

        System.out.println("[ClickGui] GUI ready, starting key hook...");

        long parentPid = ProcessHandle.current().parent().map(ProcessHandle::pid).orElse(-1L);
        if (parentPid > 0) {
            new Thread(() -> {
                while (true) {
                    try { Thread.sleep(3000); } catch (Exception e) { break; }
                    if (!ProcessHandle.of(parentPid).map(ProcessHandle::isAlive).orElse(false)) {
                        System.exit(0);
                    }
                }
            }, "ParentWatch").start();
        }

        new KeyHook(gui).run();
    }
}
