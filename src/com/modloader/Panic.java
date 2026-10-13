package com.modloader;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;

final class Panic {

    private Panic() {}

    static void run() {
        Agent.log("PANIC");
        ModuleManager.disableAll();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ClickGuiScreen) mc.setScreen(null);
        Hooks.active = false;
        System.clearProperty("sc.active");
        Agent.unhook();
        // Delete agent.jar so no traces remain on disk
        try {
            File jar = Agent.agentJarFile;
            if (jar != null && jar.exists()) jar.delete();
        } catch (Throwable ignored) {}
        // Clear temp files and delete loader (.mui) file
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        // Read loader path written by C++ on startup, then delete the .mui file
        try {
            File loaderPathFile = new File(tmp, "clickgui_loader.txt");
            if (loaderPathFile.exists()) {
                String loaderPath = new String(Files.readAllBytes(loaderPathFile.toPath()), StandardCharsets.UTF_8).trim();
                if (!loaderPath.isEmpty()) new File(loaderPath).delete();
                loaderPathFile.delete();
            }
        } catch (Throwable ignored) {}
        for (String name : new String[]{"clickgui_log.txt","clickgui_status.txt","clickgui_cmd.txt","clickgui_pause.txt"})
            new File(tmp, name).delete();
        Agent.writeStatus("PANIC_DONE");
    }
}
