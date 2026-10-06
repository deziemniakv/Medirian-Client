package dev.medirian.mc26_3.mixin;

import dev.medirian.platform.Hooks;
import dev.medirian.mc26_3.ScreenBridge;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screen changes (26.x keeps the current screen in {@link Gui}). */
@Mixin(Gui.class)
public abstract class GuiScreenMixin {

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void medirian$screen(Screen screen, CallbackInfo ci) {
        Hooks.screenChanged(screen != null);
    }

    /** Medirian's main menu instead of Minecraft's title screen: when it is opened... */
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen medirian$title(Screen screen) {
        return ScreenBridge.replaceTitle(screen);
    }

    /** ...and when setScreen(null) falls back to it outside a world. */
    @ModifyVariable(method = "setScreen", at = @At("STORE"), argsOnly = true)
    private Screen medirian$titleFallback(Screen screen) {
        return ScreenBridge.replaceTitle(screen);
    }
}
