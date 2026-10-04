package dev.meridian.mc1_21_11.mixin;

import dev.meridian.core.Log;
import dev.meridian.core.Meridian;
import dev.meridian.mc1_21_11.ModernPlatform;
import dev.meridian.mc1_21_11.SelfTestAccess;
import dev.meridian.platform.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lifecycle: boot, client tick, frame start, screen changes and shutdown. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void meridian$boot(GameConfig config, CallbackInfo ci) {
        Meridian.boot(ModernPlatform.create((Minecraft) (Object) this)).cosmetics().registerRenderer(dev.meridian.mc1_21_11.ModernCapes.RENDERER);
        if (Boolean.getBoolean("meridian.mixinAudit")) {
            // Development only: force every mixin target to load so broken injections fail fast.
            MixinEnvironment.getCurrentEnvironment().audit();
            Log.info("Mixin audit passed");
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void meridian$tick(CallbackInfo ci) {
        ModernPlatform platform = ModernPlatform.get();
        if (platform != null) {
            platform.tick();
        }
        Hooks.clientTick();
        SelfTestAccess.tick((Minecraft) (Object) this);
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void meridian$frame(boolean renderLevel, CallbackInfo ci) {
        Hooks.frameStart();
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void meridian$screen(Screen screen, CallbackInfo ci) {
        Hooks.screenChanged(screen != null);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void meridian$close(CallbackInfo ci) {
        Hooks.shutdown();
    }
}
