package dev.medirian.mc1_21_11;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.medirian.cosmetics.Cosmetic;
import dev.medirian.cosmetics.CosmeticRenderer;
import dev.medirian.cosmetics.CosmeticType;
import dev.medirian.cosmetics.model.CosmeticMesh;
import dev.medirian.cosmetics.model.CosmeticModels;
import dev.medirian.platform.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.entity.Avatar;

import java.util.Arrays;
import java.util.List;

/** Hats and wings (a render layer of the player renderer) and trail support for 1.21.11. */
public final class ModernCosmetics {

    /** Declares the worn cosmetic types this version draws. */
    public static final List<CosmeticRenderer> RENDERERS = Arrays.asList(
            renderer(CosmeticType.HAT), renderer(CosmeticType.WINGS), renderer(CosmeticType.TRAIL), renderer(CosmeticType.EMOTE));

    private ModernCosmetics() {
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
                    Textures.get(Minecraft.getInstance(), cosmetic.asset());
                }
            }
        };
    }

    /** Resolves what {@code player} wears into its render state (client thread, every frame). */
    public static void extract(Avatar player, AvatarRenderState state, float partialTick) {
        boolean local = player == Minecraft.getInstance().player;
        Cosmetic hat = Hooks.wornCosmetic(player.getUUID(), local, CosmeticType.HAT);
        Cosmetic wings = Hooks.wornCosmetic(player.getUUID(), local, CosmeticType.WINGS);
        float spread = 0f;
        if (wings != null) {
            boolean fast = player.isSprinting() || player.isFallFlying() || !player.onGround();
            spread = CosmeticModels.wingSpread(state.ageInTicks / 20.0, fast, player.getUUID().hashCode());
        }
        CosmeticRenderState cosmetics = (CosmeticRenderState) state;
        cosmetics.medirian$setCosmetics(hat, wings, spread);
        cosmetics.medirian$setEmoting(Hooks.emotePose(player.getUUID(), local, cosmetics.medirian$emoteBuffer()));
    }

    /** Applies an emote to the model after the game posed it. */
    public static void applyEmote(dev.medirian.cosmetics.emote.EmotePose pose, PlayerModel model) {
        apply(pose, dev.medirian.cosmetics.emote.EmotePose.RIGHT_ARM, model.rightArm);
        apply(pose, dev.medirian.cosmetics.emote.EmotePose.LEFT_ARM, model.leftArm);
        apply(pose, dev.medirian.cosmetics.emote.EmotePose.RIGHT_LEG, model.rightLeg);
        apply(pose, dev.medirian.cosmetics.emote.EmotePose.LEFT_LEG, model.leftLeg);
        apply(pose, dev.medirian.cosmetics.emote.EmotePose.HEAD, model.head);
        apply(pose, dev.medirian.cosmetics.emote.EmotePose.BODY, model.body);
    }

    private static void apply(dev.medirian.cosmetics.emote.EmotePose pose, int part, ModelPart model) {
        if (pose.has(part)) {
            model.xRot = pose.blend(part, 0, model.xRot);
            model.yRot = pose.blend(part, 1, model.yRot);
            model.zRot = pose.blend(part, 2, model.zRot);
        }
    }

    /** The layer drawing hats on the head and wings on the back. */
    public static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {

        public Layer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state,
                           float yRot, float xRot) {
            if (state.isInvisible) {
                return;
            }
            CosmeticRenderState cosmetics = (CosmeticRenderState) state;
            Cosmetic hat = cosmetics.medirian$hat();
            if (hat != null) {
                draw(poseStack, collector, light, getParentModel().head, CosmeticModels.model(hat.id()), hat.asset());
            }
            Cosmetic wings = cosmetics.medirian$wings();
            if (wings != null) {
                draw(poseStack, collector, light, getParentModel().body, CosmeticModels.wings(cosmetics.medirian$wingSpread()), wings.asset());
            }
        }

        private static void draw(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart part,
                                 final CosmeticMesh mesh, String asset) {
            Textures.Entry texture = Textures.get(Minecraft.getInstance(), asset);
            if (texture == null || mesh.quads() == 0) {
                return;
            }
            drawn++;
            poseStack.pushPose();
            part.translateAndRotate(poseStack);
            // meshes are in model pixels
            poseStack.scale(1f / 16f, 1f / 16f, 1f / 16f);
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(texture.id()),
                    (pose, consumer) -> emit(mesh, pose, consumer, light));
            poseStack.popPose();
        }
    }

    static void emit(CosmeticMesh mesh, PoseStack.Pose pose, VertexConsumer consumer, int light) {
        float[] d = mesh.data();
        for (int q = 0; q < mesh.quads(); q++) {
            int base = q * CosmeticMesh.STRIDE;
            float nx = d[base + 20];
            float ny = d[base + 21];
            float nz = d[base + 22];
            for (int v = 0; v < 4; v++) {
                int i = base + v * 5;
                consumer.addVertex(pose, d[i], d[i + 1], d[i + 2])
                        .setColor(-1)
                        .setUv(d[i + 3], d[i + 4])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }
}
