package com.modloader;

import net.minecraft.client.Minecraft;

final class Panic {

    private Panic() {}

    static void run() {
        Agent.log("PANIC");
        ModuleManager.disableAll();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ClickGuiScreen) mc.setScreen(null);
        Hooks.active = false;
        Agent.unhook();
        Agent.writeStatus("PANIC_DONE");
    }
}
