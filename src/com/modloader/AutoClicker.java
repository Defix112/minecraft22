package com.modloader;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;

public class AutoClicker extends Module {

    private static final double CPS = 12.0;
    private double pending;

    public AutoClicker() {
        super("AutoClicker", "12 CPS, пока зажата ЛКМ", Category.COMBAT);
    }

    @Override
    public void onTick(Minecraft mc) {
        KeyMapping attack = mc.options.keyAttack;
        boolean aimingAtBlock = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
        if (mc.player == null || mc.screen != null || !attack.isDown() || aimingAtBlock) {
            pending = 0;
            return;
        }
        pending += CPS / 20.0;
        InputConstants.Key key = InputConstants.getKey(attack.saveString());
        while (pending >= 1.0) {
            pending -= 1.0;
            KeyMapping.click(key);
        }
    }

    @Override
    protected void onDisable() {
        pending = 0;
    }
}
