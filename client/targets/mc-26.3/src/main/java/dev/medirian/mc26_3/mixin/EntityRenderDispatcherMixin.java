package dev.medirian.mc26_3.mixin;

import dev.medirian.perf.CullState;
import dev.medirian.platform.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity Culling: distance and occlusion culling of entities that passed vanilla's frustum check. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private <E extends Entity> void medirian$cull(E entity, Frustum frustum, double camX, double camY, double camZ, float partialTick,
                                                  CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        // glowing outlines are meant to be seen through walls
        if (!cir.getReturnValueZ() || entity == minecraft.getCameraEntity() || minecraft.shouldEntityAppearGlowing(entity)) {
            return;
        }
        AABB box = entity.getBoundingBox();
        if (!Hooks.shouldRenderEntity((CullState) entity, entity instanceof Player, camX, camY, camZ,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
            cir.setReturnValue(false);
        }
    }
}
