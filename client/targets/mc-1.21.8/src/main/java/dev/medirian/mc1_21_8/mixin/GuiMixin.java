package dev.medirian.mc1_21_8.mixin;

import dev.medirian.mc1_21_8.ModernPlatform;
import dev.medirian.platform.Hooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws Medirian's HUD and lets modules replace vanilla HUD parts. */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void medirian$renderHud(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        ModernPlatform platform = ModernPlatform.get();
        if (platform != null) {
            graphics.nextStratum();
            Hooks.renderHud(platform.gfx().begin(graphics), delta.getGameTimeDeltaPartialTick(false));
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void medirian$crosshair(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!Hooks.renderVanillaCrosshair()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void medirian$scoreboard(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!Hooks.renderVanillaScoreboard()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void medirian$effects(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!Hooks.renderVanillaEffects()) {
            ci.cancel();
        }
    }
}
