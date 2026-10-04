package dev.meridian.mc1_8_9.mixin;

import dev.meridian.mc1_8_9.LegacyCapes;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Meridian capes. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    // Legacy Yarn 604 names the cape getter (MCP getLocationCape) "getSkinId"; the cape renderer uses it
    @Inject(method = "getSkinId", at = @At("RETURN"), cancellable = true)
    private void meridian$cape(CallbackInfoReturnable<Identifier> cir) {
        Identifier cape = LegacyCapes.texture((AbstractClientPlayerEntity) (Object) this);
        if (cape != null) {
            cir.setReturnValue(cape);
        }
    }
}
