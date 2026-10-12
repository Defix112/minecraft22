package com.modloader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class Sprint extends Module {

    public Sprint() {
        super("Sprint", "Автоматический бег", Category.MOVEMENT);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || player.isSprinting()) return;
        if (player.input.hasForwardImpulse()
                && !player.isShiftKeyDown()
                && player.getFoodData().getFoodLevel() > 6) {
            player.setSprinting(true);
        }
    }

    @Override
    protected void onDisable() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.setSprinting(false);
    }
}
