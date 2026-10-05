package dev.medirian.mc1_8_9.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.medirian.platform.Hooks;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Item Physics for 1.8.9: {@code method_10221} (MCP func_177077_a) moves the model up by a bobbing
 * offset and spins it. Instead flat items lie down and blocks stand on the ground, turned by the
 * entity's fixed random hover offset. The copies of a stack then pile up vertically.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {

    @Redirect(method = "method_10221", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;translate(FFF)V", ordinal = 0))
    private void medirian$position(float x, float y, float z, ItemEntity item, double ex, double ey, double ez,
                                   float tickDelta, BakedModel model) {
        if (!Hooks.itemPhysics()) {
            GlStateManager.translate(x, y, z);
            return;
        }
        if (model.hasDepth()) {
            // vanilla's own base offset without the bobbing: the block stands on the ground
            float scale = model.getTransformation().getTransformation(ModelTransformation.Mode.GROUND).scale.y;
            GlStateManager.translate(x, (float) ey + 0.25f * scale, z);
        } else {
            GlStateManager.translate(x, (float) ey + 0.02f, z);
        }
    }

    @Redirect(method = "method_10221", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;rotate(FFFF)V"))
    private void medirian$rotation(float angle, float ax, float ay, float az, ItemEntity item, double ex, double ey, double ez,
                                   float tickDelta, BakedModel model) {
        if (!Hooks.itemPhysics()) {
            GlStateManager.rotate(angle, ax, ay, az);
            return;
        }
        GlStateManager.rotate(item.hoverHeight * 57.29578f, 0f, 1f, 0f);
        if (!model.hasDepth()) {
            GlStateManager.rotate(90f, 1f, 0f, 0f);
        }
    }
}
