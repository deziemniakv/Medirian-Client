package dev.medirian.mc1_8_9;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.medirian.cosmetics.Cosmetic;
import dev.medirian.cosmetics.CosmeticRenderer;
import dev.medirian.cosmetics.CosmeticType;
import dev.medirian.cosmetics.model.CosmeticMesh;
import dev.medirian.cosmetics.model.CosmeticModels;
import dev.medirian.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.model.ModelPart;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.List;

/** Hats and wings (a feature renderer of the player renderer) and trail support for 1.8.9. */
public final class LegacyCosmetics {

    /** Declares the worn cosmetic types this version draws. */
    public static final List<CosmeticRenderer> RENDERERS = Arrays.asList(
            renderer(CosmeticType.HAT), renderer(CosmeticType.WINGS), renderer(CosmeticType.TRAIL), renderer(CosmeticType.EMOTE));

    private LegacyCosmetics() {
    }

    /** Hats and wings drawn so far (the self-test checks that the layer really draws). */
    public static volatile int drawn;

    private static CosmeticRenderer renderer(final CosmeticType type) {
        return new CosmeticRenderer() {
            @Override
            public CosmeticType type() {
                return type;
            }

            @Override
            public void onEquipped(Cosmetic cosmetic) {
                if (cosmetic.asset() != null) {
                    Textures.get(MinecraftClient.getInstance(), cosmetic.asset());
                }
            }
        };
    }

    /** Draws hats on the head and wings on the back, after the player model. */
    public static final class Feature implements FeatureRenderer<AbstractClientPlayerEntity> {

        private final PlayerEntityRenderer renderer;

        public Feature(PlayerEntityRenderer renderer) {
            this.renderer = renderer;
        }

        @Override
        public void render(AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta,
                           float age, float headYaw, float headPitch, float scale) {
            if (player.isInvisible()) {
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            boolean local = player == client.player;
            Cosmetic hat = Hooks.wornCosmetic(player.getUuid(), local, CosmeticType.HAT);
            Cosmetic wings = Hooks.wornCosmetic(player.getUuid(), local, CosmeticType.WINGS);
            // a helmet, pumpkin or skull covers the head: a hat would cut through it (slot 3 = helmet)
            if (hat != null && player.getArmorSlot(3) == null) {
                draw(client, player, renderer.getModel().head, CosmeticModels.model(hat.id()), hat.asset(), scale);
            }
            if (wings != null) {
                boolean fast = player.isSprinting() || player.abilities.flying || !player.onGround;
                float spread = CosmeticModels.wingSpread(age / 20.0, fast, player.getUuid().hashCode());
                draw(client, player, renderer.getModel().body, CosmeticModels.wings(spread), wings.asset(), scale);
            }
        }

        private static void draw(MinecraftClient client, AbstractClientPlayerEntity player, ModelPart part,
                                 CosmeticMesh mesh, String asset, float scale) {
            Textures.Entry texture = Textures.get(client, asset);
            if (texture == null || mesh.quads() == 0) {
                return;
            }
            drawn++;
            GlStateManager.pushMatrix();
            // the model draws its parts 0.2 lower while sneaking, but feature renderers do it themselves
            if (player.isSneaking()) {
                GlStateManager.translate(0f, 0.2f, 0f);
            }
            part.preRender(scale);
            // meshes are in model pixels
            GlStateManager.scale(scale, scale, scale);
            client.getTextureManager().bindTexture(texture.id);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.disableCull();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_NORMAL);
            float[] d = mesh.data();
            for (int q = 0; q < mesh.quads(); q++) {
                int base = q * CosmeticMesh.STRIDE;
                for (int v = 0; v < 4; v++) {
                    int i = base + v * 5;
                    buffer.vertex(d[i], d[i + 1], d[i + 2]).texture(d[i + 3], d[i + 4])
                            .normal(d[base + 20], d[base + 21], d[base + 22]).next();
                }
            }
            tessellator.draw();
            GlStateManager.enableCull();
            GlStateManager.popMatrix();
        }

        @Override
        public boolean combineTextures() {
            return false;
        }
    }
}
