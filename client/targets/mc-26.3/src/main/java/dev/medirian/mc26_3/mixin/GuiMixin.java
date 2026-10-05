package dev.medirian.mc26_3.mixin;

import dev.medirian.mc26_3.ModernPlatform;
import dev.medirian.platform.Hooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws Medirian's HUD and lets modules replace vanilla HUD parts. */
@Mixin(Hud.class)
public abstract class GuiMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void medirian$renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        ModernPlatform platform = ModernPlatform.get();
        if (platform != null) {
            graphics.nextStratum();
            Hooks.renderHud(platform.gfx().begin(graphics), delta.getGameTimeDeltaPartialTick(false));
        }
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void medirian$crosshair(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!Hooks.renderVanillaCrosshair()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void medirian$scoreboard(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!Hooks.renderVanillaScoreboard()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void medirian$effects(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!Hooks.renderVanillaEffects()) {
            ci.cancel();
        }
    }
}
