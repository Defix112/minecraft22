package com.modloader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class Fullbright extends Module {

    public Fullbright() {
        super("Fullbright", "Максимальная яркость", Category.RENDER);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || player.hasEffect(MobEffects.NIGHT_VISION)) return;
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
            MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
    }

    @Override
    protected void onDisable() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.removeEffect(MobEffects.NIGHT_VISION);
    }
}
