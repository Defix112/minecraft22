package com.modloader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class Speed extends Module {

    private static final double BOOSTED = 0.2;
    private static final double VANILLA = 0.1;

    public Speed() {
        super("Speed", "Скорость бега x2", Category.MOVEMENT);
    }

    @Override
    public void onTick(Minecraft mc) {
        setBase(mc.player, BOOSTED);
    }

    @Override
    protected void onDisable() {
        setBase(Minecraft.getInstance().player, VANILLA);
    }

    private static void setBase(LocalPlayer player, double value) {
        if (player == null) return;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getBaseValue() != value) speed.setBaseValue(value);
    }
}
