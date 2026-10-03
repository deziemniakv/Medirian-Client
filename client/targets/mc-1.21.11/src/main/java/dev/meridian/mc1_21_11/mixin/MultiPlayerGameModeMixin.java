package dev.meridian.mc1_21_11.mixin;

import dev.meridian.mc1_21_11.AttackTargets;
import dev.meridian.platform.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attack events with reach (eye → hit point on the target's hitbox). */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void meridian$attack(Player player, Entity target, CallbackInfo ci) {
        if (!(target instanceof LivingEntity living)) {
            return;
        }
        double reach = -1;
        HitResult hit = Minecraft.getInstance().hitResult;
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() == target) {
            reach = player.getEyePosition().distanceTo(hit.getLocation());
        }
        Hooks.attack(AttackTargets.view(living), reach);
    }
}
