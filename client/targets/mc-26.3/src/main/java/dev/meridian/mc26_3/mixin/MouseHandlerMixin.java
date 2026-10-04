package dev.meridian.mc26_3.mixin;

import dev.meridian.platform.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import com.mojang.blaze3d.platform.InputConstants;
import dev.meridian.mc26_3.MouseButtons;
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

    @Inject(method = "onButton", at = @At("HEAD"))
    private void meridian$button(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        MouseButtons.set(info.button(), action != InputConstants.RELEASE);
        if (window == minecraft.getWindow().handle() && minecraft.gui.screen() == null && minecraft.player != null) {
            Hooks.mouseButton(info.button(), action == InputConstants.PRESS);
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void meridian$scroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window == minecraft.getWindow().handle() && minecraft.gui.screen() == null && Hooks.mouseScroll(vertical)) {
            ci.cancel();
        }
    }

    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void meridian$turn(LocalPlayer player, double yaw, double pitch) {
        if (Hooks.freelookActive()) {
            Hooks.freelookTurn(yaw, pitch);
        } else {
            player.turn(yaw, pitch);
        }
    }
}
