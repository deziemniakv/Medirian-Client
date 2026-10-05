package dev.medirian.mc1_21_8.mixin;

import dev.medirian.core.Log;
import dev.medirian.core.Medirian;
import dev.medirian.mc1_21_8.ModernPlatform;
import dev.medirian.mc1_21_8.SelfTestAccess;
import dev.medirian.platform.Hooks;
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
    private void medirian$boot(GameConfig config, CallbackInfo ci) {
        Medirian.boot(ModernPlatform.create((Minecraft) (Object) this)).cosmetics().registerRenderer(dev.medirian.mc1_21_8.ModernCapes.RENDERER);
        for (dev.medirian.cosmetics.CosmeticRenderer renderer : dev.medirian.mc1_21_8.ModernCosmetics.RENDERERS) {
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
        ModernPlatform platform = ModernPlatform.get();
        if (platform != null) {
            platform.tick();
        }
        Hooks.clientTick();
        SelfTestAccess.tick((Minecraft) (Object) this);
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void medirian$frame(boolean renderLevel, CallbackInfo ci) {
        Hooks.frameStart();
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void medirian$screen(Screen screen, CallbackInfo ci) {
        Hooks.screenChanged(screen != null);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void medirian$close(CallbackInfo ci) {
        Hooks.shutdown();
    }
}
