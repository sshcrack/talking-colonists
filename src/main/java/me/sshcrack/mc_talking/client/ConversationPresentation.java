package me.sshcrack.mc_talking.client;

import com.minecolonies.api.entity.citizen.AbstractCivilianEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.sshcrack.mc_talking.McTalkingClient;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.network.AiStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import org.joml.Matrix4f;

import java.util.Locale;

/** Small, depth-tested world cues; explanatory text belongs in the focused HUD. */
public final class ConversationPresentation {
    private static final int INK = 0xFF25313C;
    private static final int PAPER = 0xFFF1F4EB;
    private static final double RANGE_SQUARED = 16 * 16;

    private ConversationPresentation() {}

    public static void renderBubble(AbstractCivilianEntity citizen, PoseStack pose,
                                    MultiBufferSource buffers, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        var config = McTalkingConfig.INSTANCE.instance();
        AiStatus status = McTalkingClient.getAiStatus(citizen.getUUID());
        if (!config.showConversationBubbles || status == AiStatus.NONE || !visible(mc, citizen)) return;
        float time = config.reducedConversationMotion ? 0 : citizen.tickCount + partialTick;
        pose.pushPose();
        // Above the head, offset to the side of MineColonies' own request/status icon.
        pose.translate(0, citizen.getBbHeight() + 0.65, 0);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        /*? if neoforge {*/
        pose.scale(0.025F, -0.025F, 0.025F);
        /*?} else {*/
        /*// The 1.20 camera billboard faces the opposite local X/Z direction.
        // Positive pixel-layer depth must still move toward the viewer.
        pose.scale(-0.025F, -0.025F, -0.025F);
        *//*?}*/
        pose.translate(14, -10, 0);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        int[] layer = {0};
        pixels((x1, y1, x2, y2, color) -> quad(vertices, matrix, x1, y1, x2, y2,
                layer[0]++ * 0.002F, color), status, time);
        pose.popPose();
    }

    private static boolean visible(Minecraft mc, AbstractCivilianEntity citizen) {
        return mc.player != null && !mc.options.hideGui && citizen.isAlive() && !citizen.isSleeping()
                && !citizen.isInvisibleTo(mc.player) && mc.player.distanceToSqr(citizen) <= RANGE_SQUARED
                && mc.player.hasLineOfSight(citizen);
    }

    public static void renderFocusHint(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!McTalkingConfig.INSTANCE.instance().showConversationHint || mc.screen != null
                || !(mc.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof AbstractCivilianEntity citizen) || !visible(mc, citizen)) return;
        AiStatus status = McTalkingClient.getAiStatus(citizen.getUUID());
        if (status == AiStatus.NONE) return;
        Component hint = Component.translatable("mc_talking.conversation_hint." + status.name().toLowerCase(Locale.ROOT));
        int maxWidth = Math.max(80, Math.min(260, graphics.guiWidth() - 40));
        var lines = mc.font.split(hint, maxWidth - 30);
        int width = Math.min(maxWidth, Math.max(100,
                lines.stream().mapToInt(mc.font::width).max().orElse(0) + 30));
        int height = Math.max(28, lines.size() * 10 + 12);
        int x = (graphics.guiWidth() - width) / 2;
        int y = Math.max(6, graphics.guiHeight() - 62 - height);
        graphics.fill(x, y, x + width, y + height, 0xDC17212B);
        graphics.fill(x, y, x + 2, y + height, accent(status));
        float time = McTalkingConfig.INSTANCE.instance().reducedConversationMotion ? 0 : citizen.tickCount;
        pixels((x1, y1, x2, y2, color) -> graphics.fill(x + 7 + x1, y + 5 + y1,
                x + 7 + x2, y + 5 + y2, color), status, time);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(mc.font, lines.get(i), x + 30, y + 7 + i * 10, PAPER, false);
        }
    }

    private static int accent(AiStatus status) {
        return switch (status) {
            case TALKING -> 0xFF83D5C7;
            case LISTENING, IN_CONVERSATION -> 0xFFA6C9ED;
            case ERROR, QUOTA_EXCEEDED -> 0xFFF0A08D;
            default -> 0xFFE6C779;
        };
    }

    @FunctionalInterface
    private interface Pixels {
        void rect(int x1, int y1, int x2, int y2, int color);
    }

    /** Pixel silhouette prototyped in Blockbench; no font glyph or texture-pack dependency. */
    private static void pixels(Pixels p, AiStatus status, float time) {
        p.rect(1, 0, 15, 1, INK);
        p.rect(0, 1, 1, 11, INK);
        p.rect(15, 1, 16, 11, INK);
        p.rect(1, 11, 5, 12, INK);
        p.rect(9, 11, 15, 12, INK);
        p.rect(4, 12, 5, 15, INK);
        p.rect(5, 14, 6, 15, INK);
        p.rect(6, 13, 7, 14, INK);
        p.rect(7, 12, 8, 13, INK);
        p.rect(8, 11, 9, 12, INK);
        p.rect(1, 1, 15, 11, PAPER);
        p.rect(5, 11, 8, 12, PAPER);
        p.rect(5, 12, 7, 13, PAPER);
        p.rect(5, 13, 6, 14, PAPER);
        switch (status) {
            case TALKING -> {
                for (int i = 0; i < 3; i++) {
                    int h = 2 + (int) (2 * (1 + Math.sin(time * 0.65 + i * 1.8)));
                    p.rect(4 + i * 3, 6 - h / 2, 6 + i * 3, 6 + (h + 1) / 2, INK);
                }
            }
            case LISTENING -> {
                // Microphone: a stable, recognizable input cue.
                p.rect(7, 3, 9, 7, INK);
                p.rect(5, 5, 6, 8, INK);
                p.rect(10, 5, 11, 8, INK);
                p.rect(6, 8, 10, 9, INK);
                p.rect(7, 9, 9, 10, INK);
            }
            case ERROR, URGENT_WALKING -> {
                p.rect(7, 2, 9, 7, INK);
                p.rect(7, 8, 9, 10, INK);
            }
            case QUOTA_EXCEEDED -> {
                p.rect(5, 3, 7, 9, INK);
                p.rect(9, 3, 11, 9, INK);
            }
            case CONNECTING, RECONNECTING -> {
                int step = time == 0 ? -1 : (int) (time / 6) % 4;
                p.rect(6, 2, 10, 4, step == 0 ? accent(status) : INK);
                p.rect(10, 4, 12, 8, step == 1 ? accent(status) : INK);
                p.rect(6, 8, 10, 10, step == 2 ? accent(status) : INK);
                p.rect(4, 4, 6, 8, step == 3 ? accent(status) : INK);
            }
            case IN_CONVERSATION -> {
                p.rect(4, 3, 9, 5, INK);
                p.rect(4, 5, 6, 6, INK);
                p.rect(7, 7, 12, 9, INK);
                p.rect(10, 9, 12, 10, INK);
            }
            default -> {
                for (int i = 0; i < 3; i++) {
                    int lift = time != 0 && ((int) (time / 8) % 3 == i) ? 1 : 0;
                    p.rect(4 + i * 3, 6 - lift, 6 + i * 3, 8 - lift, INK);
                }
            }
        }
        // Corner accent pairs color with the shape above, rather than replacing it.
        p.rect(12, 1, 15, 3, accent(status));
    }

    private static void quad(VertexConsumer vertices, Matrix4f matrix, int x1, int y1, int x2, int y2, float z, int color) {
        vertex(vertices, matrix, x1, y1, z, color);
        vertex(vertices, matrix, x1, y2, z, color);
        vertex(vertices, matrix, x2, y2, z, color);
        vertex(vertices, matrix, x2, y1, z, color);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, int x, int y, float z, int color) {
        /*? if neoforge {*/
        vertices.addVertex(matrix, x, y, z).setColor(color);
        /*?} else {*/
        /*vertices.vertex(matrix, x, y, z).color(color).endVertex();
        *//*?}*/
    }
}
