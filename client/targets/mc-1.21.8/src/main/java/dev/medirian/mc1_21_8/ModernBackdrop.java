package dev.medirian.mc1_21_8;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.medirian.core.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.OptionalInt;

/**
 * The frosted backdrop of Liquid Glass HUD widgets: the world as rendered this frame, blurred.
 *
 * <p>Cheap on purpose — no extra shaders: the frame is halved again and again with linear filtering
 * (each step averages 2×2 pixels) down to 1/4…1/32 of the screen, then scaled back up step by step
 * to half size. That pyramid is a soft, even blur for a handful of tiny draws; it is made at most
 * once per frame and only while a glass widget with blur is on screen. Widgets then draw the part
 * of it behind them ({@link ModernGfx#backdrop}). Any failure turns the blur off for the session
 * (widgets keep their tint), it never breaks the HUD.
 */
final class ModernBackdrop {

    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("medirian", "runtime/backdrop");
    private static final int LEVELS = 5;

    private final Minecraft minecraft;
    private final GpuTexture[] levels = new GpuTexture[LEVELS];
    private final GpuTextureView[] views = new GpuTextureView[LEVELS];
    private final Texture texture = new Texture();
    private boolean registered;
    private boolean failed;
    /** Size of the frame the pyramid was made for. */
    int frameWidth;
    int frameHeight;
    /** Whether the backdrop holds this frame's blurred world. */
    boolean ready;
    /** Frames blurred so far (the self-test checks that the blur really runs). */
    static volatile int prepared;

    ModernBackdrop(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    /** {@code strength} 1..4: the pyramid goes down to 1/4, 1/8, 1/16 or 1/32 of the screen. */
    void prepare(int strength) {
        ready = false;
        if (failed) {
            return;
        }
        try {
            RenderTarget main = minecraft.getMainRenderTarget();
            GpuTexture source = main.getColorTexture();
            if (source == null || main.width < 32 || main.height < 32) {
                return;
            }
            resize(main.width, main.height);
            int depth = Math.max(2, Math.min(LEVELS, strength + 1));
            // the main target is sampled with nearest filtering; linear for the first halving only
            source.setTextureFilter(FilterMode.LINEAR, false);
            try {
                blit(main.getColorTextureView(), views[0]);
            } finally {
                source.setTextureFilter(main.filterMode, false);
            }
            for (int i = 1; i < depth; i++) {
                blit(views[i - 1], views[i]);
            }
            for (int i = depth - 2; i >= 0; i--) {
                blit(views[i + 1], views[i]);
            }
            ready = true;
            prepared++;
        } catch (RuntimeException e) {
            failed = true;
            Log.warn("Liquid Glass blur is not available on this system; HUD glass stays without blur", e);
        }
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
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, TextureFormat.RGBA8, w, h, 1, 1);
            levels[i].setTextureFilter(FilterMode.LINEAR, false);
            levels[i].setAddressMode(AddressMode.CLAMP_TO_EDGE);
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

    /** Draws {@code from} stretched over all of {@code to}. */
    private static void blit(GpuTextureView from, GpuTextureView to) {
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(6);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(() -> "Medirian backdrop", to, OptionalInt.empty())) {
            pass.setPipeline(RenderPipelines.TRACY_BLIT);
            pass.setVertexBuffer(0, RenderSystem.getQuadVertexBuffer());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.bindSampler("InSampler", from);
            pass.drawIndexed(0, 0, 6, 1);
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
        }

        @Override
        public void close() {
            // the pyramid owns the GPU textures
        }
    }
}
