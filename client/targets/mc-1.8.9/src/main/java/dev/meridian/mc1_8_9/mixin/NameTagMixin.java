package dev.meridian.mc1_8_9.mixin;

import dev.meridian.mc1_8_9.NameTags;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Health Tags for 1.8.9: {@code method_10256} (MCP renderName) builds the label string once and
 * draws it in both the sneaking and the normal path, so appending to that string covers both.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class NameTagMixin {

    /** The entity whose label is being built (rendering is single-threaded). */
    @Unique private LivingEntity meridian$labelEntity;

    @Inject(method = "method_10256", at = @At("HEAD"))
    private void meridian$labelStart(LivingEntity entity, double x, double y, double z, CallbackInfo ci) {
        meridian$labelEntity = entity;
    }

    @Redirect(method = "method_10256", at = @At(value = "INVOKE", target = "Lnet/minecraft/text/Text;asFormattedString()Ljava/lang/String;"))
    private String meridian$label(Text name) {
        return NameTags.label(name.asFormattedString(), meridian$labelEntity);
    }
}
