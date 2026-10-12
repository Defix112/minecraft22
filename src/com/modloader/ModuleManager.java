package com.modloader;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class ModuleManager {

    private static final List<Module> MODULES = List.of(
        new AutoClicker(),
        new Fly(),
        new Speed(),
        new Sprint(),
        new Fullbright()
    );

    private ModuleManager() {}

    public static List<Module> all() {
        return MODULES;
    }

    public static List<Module> byCategory(Category category) {
        List<Module> out = new ArrayList<>();
        for (Module m : MODULES) if (m.getCategory() == category) out.add(m);
        return out;
    }

    public static int enabledCount() {
        int n = 0;
        for (Module m : MODULES) if (m.isEnabled()) n++;
        return n;
    }

    static void tick(Minecraft mc) {
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            try {
                m.onTick(mc);
            } catch (Throwable t) {
                Agent.logError(m.getName() + ".onTick", t);
            }
        }
    }

    static void disableAll() {
        for (Module m : MODULES) m.setEnabled(false);
    }

    static String statusLine() {
        StringBuilder sb = new StringBuilder();
        for (Module m : MODULES) if (m.isEnabled()) sb.append(m.getName()).append(":ON ");
        return sb.length() == 0 ? "All OFF" : sb.toString().trim();
    }
}
