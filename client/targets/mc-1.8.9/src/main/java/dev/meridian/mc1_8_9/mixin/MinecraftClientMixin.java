package dev.meridian.mc1_8_9.mixin;

import dev.meridian.core.Log;
import dev.meridian.core.Meridian;
import dev.meridian.mc1_8_9.LegacyPlatform;
import dev.meridian.mc1_8_9.SelfTest;
import dev.meridian.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerInventory;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lifecycle, frame limiter (Dynamic FPS) and hotbar scroll (zoom). */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "initializeGame", at = @At("TAIL"))
    private void meridian$boot(CallbackInfo ci) {
        Meridian.boot(LegacyPlatform.create((MinecraftClient) (Object) this));
        if (Boolean.getBoolean("meridian.mixinAudit")) {
            // Development only: force every mixin target to load so broken injections fail fast.
            MixinEnvironment.getCurrentEnvironment().audit();
            Log.info("Mixin audit passed");
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void meridian$tick(CallbackInfo ci) {
        LegacyPlatform platform = LegacyPlatform.get();
        if (platform != null) {
            platform.tick();
        }
        Hooks.clientTick();
        SelfTest.tick((MinecraftClient) (Object) this);
    }

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void meridian$frame(CallbackInfo ci) {
        Hooks.frameStart();
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void meridian$screen(Screen screen, CallbackInfo ci) {
        Hooks.screenChanged(screen != null);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void meridian$stop(CallbackInfo ci) {
        Hooks.shutdown();
    }

    @Inject(method = "getMaxFramerate", at = @At("RETURN"), cancellable = true)
    private void meridian$framerate(CallbackInfoReturnable<Integer> cir) {
        int vanilla = cir.getReturnValueI();
        int limit = Hooks.framerateLimit(vanilla, Display.isActive(), !Display.isVisible());
        if (limit != vanilla) {
            cir.setReturnValue(limit);
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;scrollInHotbar(I)V"))
    private void meridian$scroll(PlayerInventory inventory, int amount) {
        if (!Hooks.mouseScroll(amount)) {
            inventory.scrollInHotbar(amount);
        }
    }
}
