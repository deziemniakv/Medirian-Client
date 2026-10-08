package dev.medirian.mc1_8_9.mixin;

import dev.medirian.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Settings → Advanced → Rendering → Fog: pushes the distance fog out (or off). Water and lava use
 * exponential fog (not these distances) and blindness keeps its fog.
 */
@Mixin(GameRenderer.class)
public abstract class FogMixin {

    private static float medirian$scale(float distance) {
        float scale = Hooks.fogScale();
        if (scale == 1f) {
            return distance;
        }
        Entity camera = MinecraftClient.getInstance().getCameraEntity();
        if (camera instanceof LivingEntity && ((LivingEntity) camera).hasStatusEffect(StatusEffect.BLINDNESS)) {
            return distance;
        }
        return distance * scale;
    }

    @ModifyArg(method = "renderFog", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;fogStart(F)V"), require = 1)
    private float medirian$fogStart(float start) {
        return medirian$scale(start);
    }

    @ModifyArg(method = "renderFog", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;fogEnd(F)V"), require = 1)
    private float medirian$fogEnd(float end) {
        return medirian$scale(end);
    }
}
