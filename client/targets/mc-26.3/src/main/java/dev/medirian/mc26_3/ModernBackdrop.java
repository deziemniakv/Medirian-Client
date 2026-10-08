package dev.medirian.mc26_3;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.medirian.core.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * The frosted backdrop of Liquid Glass HUD widgets: the world as rendered this frame, blurred.
 *
 * <p>Cheap on purpose — no extra shaders: the frame is halved again and again with linear filtering
 * (each step averages 2×2 pixels) down to 1/4…1/32 of the screen, then scaled back up step by step
 * to half size. That pyramid is a soft, even blur for a handful of tiny draws; it is made at most
 * once per frame and only while a glass widget with blur is on screen. Any failure turns the blur
 * off for the session (widgets keep their tint), it never breaks the HUD.
 *
 * <p>26.3 collects the HUD before it renders the world, so {@link #request} only reserves the
 * backdrop while the HUD is collected and {@link #render} makes it once the world is drawn, right
 * before the GUI is (GameRendererMixin).
 */
final class ModernBackdrop {

    static final Identifier ID = Identifier.fromNamespaceAndPath("medirian", "runtime/backdrop");
    private static final int LEVELS = 5;

    private final Minecraft minecraft;
    private final GpuTexture[] levels = new GpuTexture[LEVELS];
    private final GpuTextureView[] views = new GpuTextureView[LEVELS];
    private final Texture texture = new Texture();
    private boolean registered;
    private boolean failed;
    /** Blur strength requested for the frame being collected (0 = none). */
    private int pending;
    /** Size of the frame the pyramid was made for. */
    int frameWidth;
    int frameHeight;
    /** Whether the backdrop will hold this frame's blurred world when the GUI is drawn. */
    boolean ready;
    /** Frames blurred so far (the self-test checks that the blur really runs). */
    static volatile int prepared;

    ModernBackdrop(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    /** {@code strength} 1..4: the pyramid goes down to 1/4, 1/8, 1/16 or 1/32 of the screen. */
    void request(int strength) {
        ready = false;
        if (failed) {
            return;
        }
        try {
            RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
            if (main.getColorTexture() == null || main.width < 32 || main.height < 32) {
                return;
            }
            resize(main.width, main.height);
            pending = Math.max(2, Math.min(LEVELS, strength + 1));
            ready = true;
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    /** Makes the requested backdrop from the finished world image (before the GUI is drawn). */
    void render() {
        int depth = pending;
        pending = 0;
        if (depth == 0 || failed || levels[0] == null) {
            return;
        }
        try {
            RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
            blit(main.getColorTextureView(), views[0]);
            for (int i = 1; i < depth; i++) {
                blit(views[i - 1], views[i]);
            }
            for (int i = depth - 2; i >= 0; i--) {
                blit(views[i + 1], views[i]);
            }
            prepared++;
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    private void fail(RuntimeException e) {
        failed = true;
        ready = false;
        Log.warn("Liquid Glass blur is not available on this system; HUD glass stays without blur", e);
    }

    private void resize(int width, int height) {
        if (width == frameWidth && height == frameHeight && levels[0] != null) {
            return;
        }
        close();
        GpuDevice device = RenderSystem.getDevice();
        for (int i = 0; i < LEVELS; i++) {
            int w = Math.max(1, width >> (i + 1));
            int h = Math.max(1, height >> (i + 1));
            levels[i] = device.createTexture("Medirian backdrop " + i,
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
            views[i] = device.createTextureView(levels[i]);
        }
        frameWidth = width;
        frameHeight = height;
        texture.show(levels[0], views[0]);
        if (!registered) {
            minecraft.getTextureManager().register(ID, texture);
            registered = true;
        }
    }

    private static GpuSampler linear() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    /** Draws {@code from} stretched over all of {@code to}, with linear filtering (one full-screen triangle). */
    private static void blit(GpuTextureView from, GpuTextureView to) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(() -> "Medirian backdrop", to, Optional.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.TRACY_BLIT));
            pass.setUniform("InSampler", from, linear());
            pass.draw(3, 1, 0, 0);
        }
    }

    private void close() {
        for (int i = 0; i < LEVELS; i++) {
            if (views[i] != null) {
                views[i].close();
                views[i] = null;
            }
            if (levels[i] != null) {
                levels[i].close();
                levels[i] = null;
            }
        }
    }

    /** The finished backdrop as a texture the GUI can draw by {@link #ID}. Its GPU textures belong to the pyramid. */
    private static final class Texture extends AbstractTexture {

        void show(GpuTexture gpuTexture, GpuTextureView view) {
            this.texture = gpuTexture;
            this.textureView = view;
            this.sampler = linear();
        }

        @Override
        public void close() {
            // the pyramid owns the GPU textures
        }
    }
}
