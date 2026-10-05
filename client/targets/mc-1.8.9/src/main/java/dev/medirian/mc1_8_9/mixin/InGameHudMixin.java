package dev.medirian.mc1_8_9.mixin;

import dev.medirian.mc1_8_9.LegacyGfx;
import dev.medirian.mc1_8_9.LegacyPlatform;
import dev.medirian.platform.Hooks;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Draws Medirian's HUD and lets modules replace vanilla HUD parts. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void medirian$renderHud(float tickDelta, CallbackInfo ci) {
        LegacyPlatform platform = LegacyPlatform.get();
        if (platform != null) {
            LegacyGfx gfx = platform.gfx().begin();
            Hooks.renderHud(gfx, tickDelta);
            gfx.end();
        }
    }

    @Inject(method = "showCrosshair", at = @At("RETURN"), cancellable = true)
    private void medirian$crosshair(CallbackInfoReturnable<Boolean> cir) {
        if (!Hooks.renderVanillaCrosshair()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "renderScoreboardObjective", at = @At("HEAD"), cancellable = true)
    private void medirian$scoreboard(ScoreboardObjective objective, Window window, CallbackInfo ci) {
        if (!Hooks.renderVanillaScoreboard()) {
            ci.cancel();
        }
    }
}
