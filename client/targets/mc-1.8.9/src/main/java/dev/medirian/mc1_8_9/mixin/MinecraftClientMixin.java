package dev.medirian.mc1_8_9.mixin;

import dev.medirian.core.Log;
import dev.medirian.core.Medirian;
import dev.medirian.mc1_8_9.LegacyPlatform;
import dev.medirian.mc1_8_9.SelfTest;
import dev.medirian.platform.Hooks;
import dev.medirian.mc1_8_9.ScreenBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerInventory;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lifecycle, frame limiter (Dynamic FPS) and hotbar scroll (zoom). */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "initializeGame", at = @At("TAIL"))
    private void medirian$boot(CallbackInfo ci) {
        Medirian.boot(LegacyPlatform.create((MinecraftClient) (Object) this)).cosmetics().registerRenderer(dev.medirian.mc1_8_9.LegacyCapes.RENDERER);
        for (dev.medirian.cosmetics.CosmeticRenderer renderer : dev.medirian.mc1_8_9.LegacyCosmetics.RENDERERS) {
            Medirian.get().cosmetics().registerRenderer(renderer);
        }
        if (Boolean.getBoolean("medirian.mixinAudit")) {
            // Development only: force every mixin target to load so broken injections fail fast.
            MixinEnvironment.getCurrentEnvironment().audit();
            Log.info("Mixin audit passed");
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void medirian$tick(CallbackInfo ci) {
        LegacyPlatform platform = LegacyPlatform.get();
        if (platform != null) {
            platform.tick();
        }
        Hooks.clientTick();
        SelfTest.tick((MinecraftClient) (Object) this);
    }

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void medirian$frame(CallbackInfo ci) {
        Hooks.frameStart();
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void medirian$screen(Screen screen, CallbackInfo ci) {
        Hooks.screenChanged(screen != null);
    }

    /** Medirian's main menu instead of Minecraft's title screen: when it is opened... */
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen medirian$title(Screen screen) {
        return ScreenBridge.replaceTitle((MinecraftClient) (Object) this, screen);
    }

    /** ...and when setScreen(null) falls back to it outside a world. */
    @ModifyVariable(method = "setScreen", at = @At("STORE"), argsOnly = true)
    private Screen medirian$titleFallback(Screen screen) {
        return ScreenBridge.replaceTitle((MinecraftClient) (Object) this, screen);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void medirian$stop(CallbackInfo ci) {
        Hooks.shutdown();
    }

    @Inject(method = "getMaxFramerate", at = @At("RETURN"), cancellable = true)
    private void medirian$framerate(CallbackInfoReturnable<Integer> cir) {
        int vanilla = cir.getReturnValueI();
        int limit = Hooks.framerateLimit(vanilla, Display.isActive(), !Display.isVisible());
        if (limit != vanilla) {
            cir.setReturnValue(limit);
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;scrollInHotbar(I)V"))
    private void medirian$scroll(PlayerInventory inventory, int amount) {
        if (!Hooks.mouseScroll(amount)) {
            inventory.scrollInHotbar(amount);
        }
    }
}
