package me.sshcrack.mc_talking.client;

import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.internal.audio.SpeechEnvelope;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/** Small head-space opening, preserving the original skin during silence. */
public final class CitizenMouthLayer extends RenderLayer<AbstractEntityCitizen, CitizenModel<AbstractEntityCitizen>> {
    /*? if neoforge {*/
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("neoforge", "textures/white.png");
    /*?} else {*/
    /*private static final ResourceLocation WHITE = new ResourceLocation("forge", "textures/white.png");
    *//*?}*/

    public CitizenMouthLayer(RenderLayerParent<AbstractEntityCitizen, CitizenModel<AbstractEntityCitizen>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractEntityCitizen citizen,
            float swing, float amount, float partial, float age, float yaw, float pitch) {
        var config = McTalkingConfig.INSTANCE.instance();
        var model = getParentModel();
        if (!config.showConversationMouths || config.reducedConversationMotion || !model.head.visible
                || citizen.isInvisible() || citizen.isSleeping() || !citizen.isAlive() || citizen.isBaby()
                || !citizen.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        var view = citizen.getCitizenDataView();
        if (view != null && (view.getCustomTexture() != null
                || !view.getDisplayArmor(EquipmentSlot.HEAD).isEmpty())) return;
        float opening = SpeechEnvelope.opening(citizen.getUUID());
        if (opening < 0.025F) return;
        pose.pushPose();
        model.head.translateAndRotate(pose);
        pose.scale(1F / 16, 1F / 16, 1F / 16);
        var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        float height = 0.35F + opening * 1.15F;
        float width = 1.4F + opening * 0.6F;
        rect(vertices, pose, -width / 2, -2, width / 2, -2 + height, -4.52F, 0xFF39242A, light);
        if (opening > 0.4F) {
            rect(vertices, pose, -0.6F, -2, 0.6F, -1.78F, -4.525F, 0xFFE2D3BD, light);
            rect(vertices, pose, -0.45F, -2 + height - 0.22F, 0.45F, -2 + height, -4.525F, 0xFFB66E71, light);
        }
        pose.popPose();
    }

    private static void rect(VertexConsumer v, PoseStack p, float x1, float y1, float x2, float y2,
            float z, int color, int light) {
        vertex(v, p, x1, y1, z, color, light);
        vertex(v, p, x1, y2, z, color, light);
        vertex(v, p, x2, y2, z, color, light);
        vertex(v, p, x2, y1, z, color, light);
    }

    private static void vertex(VertexConsumer v, PoseStack p, float x, float y, float z, int color, int light) {
        /*? if neoforge {*/
        v.addVertex(p.last(), x, y, z).setColor(color).setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p.last(), 0, 0, -1);
        /*?} else {*/
        /*v.vertex(p.last().pose(), x, y, z).color(color).uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.last().normal(), 0, 0, -1).endVertex();
        *//*?}*/
    }
}
