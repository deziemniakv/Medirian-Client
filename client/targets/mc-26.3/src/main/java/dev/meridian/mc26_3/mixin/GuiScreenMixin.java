package dev.meridian.mc26_3.mixin;

import dev.meridian.platform.Hooks;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screen changes (26.x keeps the current screen in {@link Gui}). */
@Mixin(Gui.class)
public abstract class GuiScreenMixin {

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void meridian$screen(Screen screen, CallbackInfo ci) {
        Hooks.screenChanged(screen != null);
    }
}
