package dev.meridian.mc1_8_9.mixin;

import dev.meridian.cosmetics.emote.EmotePose;
import dev.meridian.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes: override the pose after the game set it up. In 1.8.9 the second skin layer (sleeves,
 * pants, jacket, hat) copies the angles at the end of setAngles, so they are copied again.
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {

    @Unique private static final EmotePose POSE = new EmotePose();

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void meridian$emote(float limbAngle, float limbDistance, float age, float headYaw, float headPitch, float scale,
                                Entity entity, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity)
                || !Hooks.emotePose(entity.getUuid(), entity == MinecraftClient.getInstance().player, POSE)) {
            return;
        }
        PlayerEntityModel model = (PlayerEntityModel) (Object) this;
        meridian$apply(EmotePose.RIGHT_ARM, model.rightArm, model.rightSleeve);
        meridian$apply(EmotePose.LEFT_ARM, model.leftArm, model.leftSleeve);
        meridian$apply(EmotePose.RIGHT_LEG, model.rightLeg, model.rightPants);
        meridian$apply(EmotePose.LEFT_LEG, model.leftLeg, model.leftPants);
        meridian$apply(EmotePose.HEAD, model.head, model.hat);
        meridian$apply(EmotePose.BODY, model.body, model.jacket);
    }

    @Unique
    private static void meridian$apply(int part, ModelPart model, ModelPart layer) {
        if (!POSE.has(part)) {
            return;
        }
        model.posX = POSE.blend(part, 0, model.posX);
        model.posY = POSE.blend(part, 1, model.posY);
        model.posZ = POSE.blend(part, 2, model.posZ);
        layer.posX = model.posX;
        layer.posY = model.posY;
        layer.posZ = model.posZ;
    }
}
