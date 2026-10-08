package dev.medirian.mc1_8_9;

import com.mojang.blaze3d.platform.GLX;
import dev.medirian.core.Log;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTFramebufferBlit;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * The frosted backdrop of Liquid Glass HUD widgets in 1.8.9: the world as rendered this frame,
 * blurred.
 *
 * <p>Cheap on purpose — no shaders: the frame is copied into framebuffers of half, quarter… size
 * with linear filtering ({@code glBlitFramebuffer}, each step averages 2×2 pixels) down to
 * 1/4…1/32 of the screen, then scaled back up step by step to half size. That pyramid is a soft,
 * even blur for a handful of copies; it is made at most once per frame and only while a glass
 * widget with blur is on screen. Without framebuffer blits (very old drivers) or after any error the
 * blur is off for the session and widgets keep their tint.
 */
final class LegacyBackdrop {

    private static final int LEVELS = 5;

    private final MinecraftClient client;
    private final Framebuffer[] levels = new Framebuffer[LEVELS];
    private Boolean blitSupport;
    private boolean useGl30;
    private boolean failed;
    /** Size of the frame the pyramid was made for. */
    int frameWidth;
    int frameHeight;
    /** Whether the backdrop holds this frame's blurred world. */
    boolean ready;
    /** Frames blurred so far (the self-test checks that the blur really runs). */
    static volatile int prepared;

    LegacyBackdrop(MinecraftClient client) {
        this.client = client;
    }

    /** The GL texture holding the finished backdrop (half the frame size). */
    int texture() {
        return levels[0].colorAttachment;
    }

    private boolean supported() {
        if (blitSupport == null) {
            ContextCapabilities caps = GLContext.getCapabilities();
            useGl30 = caps.OpenGL30;
            blitSupport = GLX.supportsFbo() && (caps.OpenGL30 || caps.GL_EXT_framebuffer_blit);
            if (!blitSupport) {
                Log.info("Liquid Glass blur needs framebuffer blits, which this graphics driver lacks; HUD glass stays without blur");
            }
        }
        return blitSupport;
    }

    /** {@code strength} 1..4: the pyramid goes down to 1/4, 1/8, 1/16 or 1/32 of the screen. */
    void prepare(int strength) {
        ready = false;
        if (failed || !supported()) {
            return;
        }
        Framebuffer main = client.getFramebuffer();
        if (main == null || main.fbo < 0 || client.width < 32 || client.height < 32) {
            return;
        }
        try {
            resize(client.width, client.height);
            int depth = Math.max(2, Math.min(LEVELS, strength + 1));
            blit(main.fbo, main.textureWidth, main.textureHeight, levels[0]);
            for (int i = 1; i < depth; i++) {
                blit(levels[i - 1], levels[i]);
            }
            for (int i = depth - 2; i >= 0; i--) {
                blit(levels[i + 1], levels[i]);
            }
            ready = true;
            prepared++;
        } catch (RuntimeException e) {
            failed = true;
            Log.warn("Liquid Glass blur is not available on this system; HUD glass stays without blur", e);
        } finally {
            // back to drawing the frame (creating framebuffers unbinds it)
            main.bind(false);
        }
    }

    private void resize(int width, int height) {
        if (width == frameWidth && height == frameHeight && levels[0] != null) {
            return;
        }
        for (int i = 0; i < LEVELS; i++) {
            int w = Math.max(1, width >> (i + 1));
            int h = Math.max(1, height >> (i + 1));
            if (levels[i] == null) {
                levels[i] = new Framebuffer(w, h, false);
            } else {
                levels[i].resize(w, h);
            }
            levels[i].setTexFilter(GL11.GL_LINEAR);
        }
        frameWidth = width;
        frameHeight = height;
    }

    private void blit(Framebuffer from, Framebuffer to) {
        blit(from.fbo, from.textureWidth, from.textureHeight, to);
    }

    /** Copies all of framebuffer {@code from} stretched over all of {@code to}, with linear filtering. */
    private void blit(int from, int width, int height, Framebuffer to) {
        if (useGl30) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.fbo);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, to.textureWidth, to.textureHeight, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        } else {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferBlit.GL_READ_FRAMEBUFFER_EXT, from);
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferBlit.GL_DRAW_FRAMEBUFFER_EXT, to.fbo);
            EXTFramebufferBlit.glBlitFramebufferEXT(0, 0, width, height, 0, 0, to.textureWidth, to.textureHeight,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        }
    }
}
