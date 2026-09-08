package me.sshcrack.mc_talking.devtools;

import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.McTalkingClient;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Real renderer regression checks and screenshots, confined to disposable auto-quit worlds. */
public final class DevConversationVisualVerification {
    private DevConversationVisualVerification() {}

    public static void prepare(MinecraftServer server, EntityCitizen serverCitizen) throws Exception {
        if (!Boolean.getBoolean("mc_talking.autoQuit")) throw new IllegalStateException("Requires disposable smoke world");
        server.submit(() -> {
            var player = server.getPlayerList().getPlayers().get(0);
            var world = player.serverLevel();
            int x = serverCitizen.blockPosition().getX();
            int z = serverCitizen.blockPosition().getZ();
            int y = Math.max(110, world.getSeaLevel() + 30);
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 6; dz++) {
                    world.setBlockAndUpdate(new BlockPos(x + dx, y - 1, z + dz), Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
            world.setDayTime(6000);
            world.setWeatherParameters(6000, 0, false, false);
            serverCitizen.teleportTo(x + .5, y, z + .5);
            serverCitizen.setYRot(0);
            serverCitizen.setYHeadRot(0);
            serverCitizen.yBodyRot = 0;
            player.teleportTo(x + .5, y, z + 3.2);
            player.setYRot(180);
            player.setXRot(0);
        }).get(5, TimeUnit.SECONDS);
    }

    public static void verify(MinecraftServer server, EntityCitizen serverCitizen) throws Exception {
        if (!Boolean.getBoolean("mc_talking.autoQuit")) throw new IllegalStateException("Requires disposable smoke world");
        Minecraft mc = Minecraft.getInstance();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        AbstractEntityCitizen citizen = null;
        while (citizen == null && System.nanoTime() < deadline) {
            citizen = mc.submit(() -> mc.level.getEntity(serverCitizen.getId()) instanceof AbstractEntityCitizen c ? c : null)
                    .get(5, TimeUnit.SECONDS);
            if (citizen == null) Thread.sleep(50);
        }
        if (citizen == null) throw new IllegalStateException("Fixture citizen never reached client");
        AbstractEntityCitizen clientCitizen = citizen;
        boolean hideGui = mc.options.hideGui;
        boolean reduced = McTalkingConfig.INSTANCE.instance().reducedConversationMotion;
        boolean bubbles = McTalkingConfig.INSTANCE.instance().showConversationBubbles;
        boolean hint = McTalkingConfig.INSTANCE.instance().showConversationHint;
        try {
            mc.submit(() -> {
                McTalkingConfig.INSTANCE.instance().reducedConversationMotion = false;
                McTalkingConfig.INSTANCE.instance().showConversationBubbles = true;
                McTalkingConfig.INSTANCE.instance().showConversationHint = true;
                mc.player.setYRot(180);
                mc.player.setXRot(0);
                mc.options.hideGui = false;
            }).get(5, TimeUnit.SECONDS);
            Thread.sleep(750);
            mc.submit(() -> verifyPoseReset(mc, clientCitizen)).get(5, TimeUnit.SECONDS);
            for (AiStatus status : new AiStatus[]{AiStatus.TALKING, AiStatus.LISTENING, AiStatus.THINKING, AiStatus.ERROR}) {
                server.submit(() -> AiStatusHelper.setAiStatusOnServerThread(serverCitizen, status)).get(5, TimeUnit.SECONDS);
                long statusDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (McTalkingClient.getAiStatus(clientCitizen.getUUID()) != status && System.nanoTime() < statusDeadline) {
                    Thread.sleep(50);
                }
                require(McTalkingClient.getAiStatus(clientCitizen.getUUID()) == status, "synced " + status);
                Thread.sleep(500);
                CompletableFuture<Void> saved = new CompletableFuture<>();
                mc.execute(() -> Screenshot.grab(new File("/tmp/colonist-redesign"),
                        "conversation-" + net.minecraft.SharedConstants.getCurrentVersion().getName() + "-" + status.name().toLowerCase(java.util.Locale.ROOT) + ".png",
                        mc.getMainRenderTarget(), message -> saved.complete(null)));
                saved.get(5, TimeUnit.SECONDS);
            }
            server.submit(() -> AiStatusHelper.setAiStatusOnServerThread(serverCitizen, AiStatus.NONE)).get(5, TimeUnit.SECONDS);
            McTalking.LOGGER.info("MC_TALKING_VISUAL_SUCCESS:poses,reset,reduced-motion,synced-states,screenshots");
        } finally {
            mc.submit(() -> {
                mc.options.hideGui = hideGui;
                McTalkingConfig.INSTANCE.instance().reducedConversationMotion = reduced;
                McTalkingConfig.INSTANCE.instance().showConversationBubbles = bubbles;
                McTalkingConfig.INSTANCE.instance().showConversationHint = hint;
            }).get(5, TimeUnit.SECONDS);
        }
    }

    private static void verifyPoseReset(Minecraft mc, AbstractEntityCitizen citizen) {
        var renderer = mc.getEntityRenderDispatcher().getRenderer(citizen);
        require(renderer instanceof LivingEntityRenderer<?, ?>, "living citizen renderer");
        var rawModel = ((LivingEntityRenderer<?, ?>) renderer).getModel();
        require(rawModel instanceof CitizenModel<?>, "profession model preserved");
        CitizenModel<?> model = (CitizenModel<?>) rawModel;
        model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        McTalkingClient.updateAiStatus(citizen.getUUID(), AiStatus.NONE);
        model.setupAnim(citizen, 0, 0, 19, 0, 0);
        float idleArm = model.rightArm.xRot;
        float idleHead = model.head.xRot;
        McTalkingClient.updateAiStatus(citizen.getUUID(), AiStatus.TALKING);
        model.setupAnim(citizen, 0, 0, 19, 0, 0);
        require(Math.abs(model.rightArm.xRot - idleArm) > 0.1F, "speaking gesture injected");
        McTalkingClient.updateAiStatus(citizen.getUUID(), AiStatus.NONE);
        model.setupAnim(citizen, 0, 0, 19, 0, 0);
        require(Math.abs(model.rightArm.xRot - idleArm) < 0.0001F, "shared arm pose restored");
        require(Math.abs(model.head.xRot - idleHead) < 0.0001F, "shared head pose restored");
        McTalkingConfig.INSTANCE.instance().reducedConversationMotion = true;
        McTalkingClient.updateAiStatus(citizen.getUUID(), AiStatus.TALKING);
        model.setupAnim(citizen, 0, 0, 19, 0, 0);
        require(Math.abs(model.rightArm.xRot - idleArm) < 0.0001F, "reduced motion respected");
        McTalkingConfig.INSTANCE.instance().reducedConversationMotion = false;
        McTalkingClient.updateAiStatus(citizen.getUUID(), AiStatus.NONE);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("Visual verification failed: " + message);
    }
}
