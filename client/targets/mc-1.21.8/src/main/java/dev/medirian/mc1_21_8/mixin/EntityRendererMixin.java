package dev.medirian.mc1_21_8.mixin;

import dev.medirian.module.impl.combat.HealthTagsModule;
import dev.medirian.platform.Hooks;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Name tag distance (Settings → Advanced → Visibility) and Health Tags: health appended to name tags. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("RETURN"))
    private void medirian$healthTag(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        if (state.nameTag != null && !Hooks.nameTagVisible(state.distanceToCameraSq)) {
            state.nameTag = null;
        }
        if (state.nameTag == null || !(entity instanceof LivingEntity living)) {
            return;
        }
        HealthTagsModule tags = Hooks.healthTags(entity instanceof Player);
        if (tags == null) {
            return;
        }
        float health = living.getHealth();
        MutableComponent tag = Component.empty().append(state.nameTag).append(Component.literal(" " + tags.text(health))
                .withColor(HealthTagsModule.color(health, living.getMaxHealth()) & 0xFFFFFF));
        String absorption = tags.absorptionText(living.getAbsorptionAmount());
        if (absorption != null) {
            tag.append(Component.literal(" " + absorption).withColor(HealthTagsModule.ABSORPTION & 0xFFFFFF));
        }
        state.nameTag = tag;
    }
}
