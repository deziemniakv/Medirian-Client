package dev.meridian.mc1_21_8.mixin;

import dev.meridian.perf.CullState;
import dev.meridian.platform.Hooks;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Entity Culling: occlusion culling of block entities (chests, signs, heads, banners…). */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/phys/Vec3;)Z"))
    private boolean meridian$shouldRender(BlockEntityRenderer renderer, BlockEntity blockEntity, Vec3 cameraPos) {
        if (!renderer.shouldRender(blockEntity, cameraPos)) {
            return false;
        }
        // beacon beams, end gateway beams and structure outlines reach far outside the block
        if (renderer.shouldRenderOffScreen()) {
            return true;
        }
        BlockPos pos = blockEntity.getBlockPos();
        return Hooks.shouldRenderBlockEntity((CullState) blockEntity, cameraPos.x, cameraPos.y, cameraPos.z,
                pos.getX(), pos.getY(), pos.getZ());
    }
}
