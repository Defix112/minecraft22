package com.modloader;

import net.minecraft.client.Minecraft;

public abstract class Module {

    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public boolean isEnabled() { return enabled; }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean value) {
        if (value == enabled) return;
        enabled = value;
        try {
            if (value) onEnable(); else onDisable();
        } catch (Throwable t) {
            Agent.logError(name + (value ? ".onEnable" : ".onDisable"), t);
        }
        Agent.log(name + (value ? " ON" : " OFF"));
        Agent.writeStatus(ModuleManager.statusLine());
    }

    protected void onEnable() {}

    protected void onDisable() {}

    public void onTick(Minecraft mc) {}
}
