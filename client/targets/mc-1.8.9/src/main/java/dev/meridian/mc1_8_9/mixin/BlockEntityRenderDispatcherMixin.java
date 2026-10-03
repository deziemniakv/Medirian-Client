package dev.meridian.mc1_8_9.mixin;

import dev.meridian.perf.CullState;
import dev.meridian.platform.Hooks;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entity Culling: occlusion culling of block entities (chests, signs, heads, banners…). */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

    /** The view entity's (interpolated) feet position; the camera sits at {@link Camera#getPosition()} relative to it. */
    @Shadow public double cameraX;
    @Shadow public double cameraY;
    @Shadow public double cameraZ;

    /** Runs after vanilla's distance check passed, before any GL state is touched. */
    @Inject(method = "renderEntity(Lnet/minecraft/block/entity/BlockEntity;FI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getLight(Lnet/minecraft/util/math/BlockPos;I)I"),
            cancellable = true)
    private void meridian$cull(BlockEntity blockEntity, float tickDelta, int destroyProgress, CallbackInfo ci) {
        // beacon beams reach far outside the block
        if (blockEntity instanceof BeaconBlockEntity) {
            return;
        }
        Vec3d offset = Camera.getPosition();
        BlockPos pos = blockEntity.getPos();
        if (!Hooks.shouldRenderBlockEntity((CullState) blockEntity, cameraX + offset.x, cameraY + offset.y, cameraZ + offset.z,
                pos.getX(), pos.getY(), pos.getZ())) {
            ci.cancel();
        }
    }
}
