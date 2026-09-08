package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.McTalkingClient;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.network.AiStatus;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds speech gestures after vanilla posing, preserving profession models and held-item poses. */
@Mixin(value = CitizenModel.class, remap = false)
public abstract class CitizenConversationAnimationMixin extends HumanoidModel<AbstractEntityCitizen> {
    protected CitizenConversationAnimationMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim(Lcom/minecolonies/api/entity/citizen/AbstractEntityCitizen;FFFFF)V", at = @At("RETURN"))
    private void mc_talking$animateConversation(AbstractEntityCitizen citizen, float limbSwing,
            float limbSwingAmount, float age, float yaw, float pitch, CallbackInfo ci) {
        // Sleeping, fighting and working retain their original poses. Vanilla resets the parts
        // each setupAnim call, so shared profession models cannot retain another citizen's pose.
        if (McTalkingConfig.INSTANCE.instance().reducedConversationMotion || citizen.isSleeping()
                || !citizen.isAlive() || citizen.isUsingItem() || citizen.swinging
                || citizen.getRenderMetadata().contains("working")) return;
        AiStatus status = McTalkingClient.getAiStatus(citizen.getUUID());
        if (status == AiStatus.NONE) return;
        java.util.UUID partnerId = McTalkingClient.getConversationPartner(citizen.getUUID());
        var partner = partnerId == null ? null : citizen.level().getPlayerByUUID(partnerId);
        if (partner != null && limbSwingAmount < 0.1F && citizen.distanceToSqr(partner) < 64) {
            double dx = partner.getX() - citizen.getX();
            double dz = partner.getZ() - citizen.getZ();
            float target = (float) (Math.atan2(dz, dx) * 180 / Math.PI - 90) - citizen.yBodyRot;
            target = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(target), -55, 55);
            head.yRot += (target * ((float) Math.PI / 180) - head.yRot) * 0.35F;
        }
        float phase = age + (citizen.getId() % 17) * 3;
        if (status == AiStatus.TALKING) {
            head.xRot += (float) Math.sin(phase * 0.48) * 0.045F;
            head.yRot += (float) Math.sin(phase * 0.19) * 0.035F;
            // Gesture only with empty hands and while standing: no waving tools or sliding arms.
            if (limbSwingAmount < 0.1F && !citizen.isPassenger()) {
                float gesture = 0.5F + 0.5F * (float) Math.sin(phase * 0.16);
                if (rightArmPose == HumanoidModel.ArmPose.EMPTY) {
                    rightArm.xRot -= 0.20F + gesture * 0.30F;
                    rightArm.zRot += 0.10F + gesture * 0.12F;
                }
                if (leftArmPose == HumanoidModel.ArmPose.EMPTY) {
                    leftArm.xRot -= 0.12F + (1 - gesture) * 0.18F;
                    leftArm.zRot -= 0.08F;
                }
            }
        } else if (status == AiStatus.LISTENING || status == AiStatus.IN_CONVERSATION) {
            head.xRot += 0.035F * (float) Math.sin(phase * 0.10);
        } else if (status == AiStatus.THINKING) {
            head.yRot += 0.08F;
            head.xRot -= 0.04F;
        }
        hat.copyFrom(head);
    }
}
