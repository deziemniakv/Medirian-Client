package dev.medirian.mc1_8_9.mixin;

import dev.medirian.mc1_8_9.AttackTargets;
import dev.medirian.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attack events with reach (eye → hit point on the target's hitbox). */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void medirian$attack(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (!(target instanceof LivingEntity)) {
            return;
        }
        double reach = -1;
        BlockHitResult hit = MinecraftClient.getInstance().result;
        if (hit != null && hit.entity == target && hit.pos != null) {
            reach = player.getCameraPosVec(1f).distanceTo(hit.pos);
        }
        Hooks.attack(AttackTargets.view((LivingEntity) target), reach);
    }
}
