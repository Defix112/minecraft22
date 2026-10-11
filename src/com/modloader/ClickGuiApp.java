package com.modloader;

public class ClickGuiApp {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");

        System.out.println("[ClickGui] Starting standalone GUI...");

        ClickGui gui = new ClickGui();
        gui.show();

        System.out.println("[ClickGui] GUI visible, starting key hook...");

        // monitor parent process - exit if Minecraft closes
        long parentPid = ProcessHandle.current().parent().map(ProcessHandle::pid).orElse(-1L);
        if (parentPid > 0) {
            new Thread(() -> {
                while (true) {
                    try { Thread.sleep(3000); } catch (Exception e) { break; }
                    if (!ProcessHandle.of(parentPid).map(ProcessHandle::isAlive).orElse(false)) {
                        System.out.println("[ClickGui] Parent process died, exiting");
                        System.exit(0);
                    }
                }
            }, "ParentWatch").start();
        }

        KeyHook hook = new KeyHook(gui);
        hook.run();
    }
}
