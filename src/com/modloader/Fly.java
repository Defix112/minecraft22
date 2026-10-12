package com.modloader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;

public class Fly extends Module {

    public Fly() {
        super("Fly", "Полёт в выживании", Category.MOVEMENT);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        player.getAbilities().mayfly = true;
    }

    @Override
    protected void onDisable() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isCreative() || player.isSpectator()) return;
        Abilities abilities = player.getAbilities();
        abilities.mayfly = false;
        abilities.flying = false;
    }
}
