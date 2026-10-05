package dev.medirian.mc1_21_8.mixin;

import dev.medirian.platform.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse buttons (CPS, keybinds), scroll (zoom) and camera turning (freelook). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onPress", at = @At("HEAD"))
    private void medirian$button(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (window == minecraft.getWindow().getWindow() && minecraft.screen == null && minecraft.player != null) {
            Hooks.mouseButton(button, action == GLFW.GLFW_PRESS);
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void medirian$scroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window == minecraft.getWindow().getWindow() && minecraft.screen == null && Hooks.mouseScroll(vertical)) {
            ci.cancel();
        }
    }

    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void medirian$turn(LocalPlayer player, double yaw, double pitch) {
        if (Hooks.freelookActive()) {
            Hooks.freelookTurn(yaw, pitch);
        } else {
            player.turn(yaw, pitch);
        }
    }
}
