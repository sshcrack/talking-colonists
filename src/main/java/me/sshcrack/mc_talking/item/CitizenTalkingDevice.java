package me.sshcrack.mc_talking.item;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.Config;
import me.sshcrack.mc_talking.MinecoloniesTalkingCitizens;
import me.sshcrack.mc_talking.manager.TalkingManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

public class CitizenTalkingDevice extends Item {

    public CitizenTalkingDevice() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.mc_talking.talking_device.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, @NotNull Player player, @NotNull LivingEntity entity, @NotNull InteractionHand hand) {
        // Check if the entity is a citizen
        if (!(entity instanceof AbstractEntityCitizen citizen)) {
            return InteractionResult.PASS;
        }

        if (player.level().isClientSide()) {
            // Client side - just show some visual feedback
            return InteractionResult.SUCCESS;
        }

        // Server side logic
        ServerPlayer serverPlayer = (ServerPlayer) player;
        UUID playerId = serverPlayer.getUUID();
        UUID citizenId = citizen.getUUID();

        // Check if API key is set
        if (Config.geminiApiKey.isEmpty()) {
            serverPlayer.sendSystemMessage(
                    Component.literal("No Gemini API key set. Minecolonies Talking Citizens is disabled.")
                            .withStyle(ChatFormatting.RED)
            );
            return InteractionResult.FAIL;
        }

        // Check if voice chat API is initialized
        if (vcApi == null) {
            serverPlayer.sendSystemMessage(
                    Component.literal("Voice chat API is not initialized.")
                            .withStyle(ChatFormatting.RED)
            );
            return InteractionResult.FAIL;
        }

        // Check distance before allowing interaction
        double distance = player.distanceToSqr(citizen);
        if (distance > (Config.activationDistance * Config.activationDistance)) {
            serverPlayer.sendSystemMessage(
                    Component.literal("This citizen is too far away to talk to.")
                            .withStyle(ChatFormatting.RED)
            );
            return InteractionResult.FAIL;
        }

        // Handle interaction with citizen
        handleCitizenInteraction(serverPlayer, playerId, citizen, citizenId);

        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean onLeftClickEntity(@NotNull ItemStack stack, @NotNull Player player, @NotNull Entity entity) {
        if (!(entity instanceof AbstractEntityCitizen citizen)) {
            return false; // Allow normal attack behavior for non-citizens
        }

        if (player.level().isClientSide()) {
            return true; // Prevent attack on client side
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        UUID playerId = serverPlayer.getUUID();
        UUID citizenId = citizen.getUUID();

        // Check if API key is set
        if (Config.geminiApiKey.isEmpty()) {
            serverPlayer.sendSystemMessage(
                    Component.literal("No Gemini API key set. Minecolonies Talking Citizens is disabled.")
                            .withStyle(ChatFormatting.RED)
            );
            return true; // Still prevent attack
        }

        // Check if voice chat API is initialized
        if (vcApi == null) {
            serverPlayer.sendSystemMessage(
                    Component.literal("Voice chat API is not initialized.")
                            .withStyle(ChatFormatting.RED)
            );
            return true; // Still prevent attack
        }

        // Check distance before allowing interaction
        double distance = player.distanceToSqr(citizen);
        if (distance > (Config.activationDistance * Config.activationDistance)) {
            serverPlayer.sendSystemMessage(
                    Component.literal("This citizen is too far away to talk to.")
                            .withStyle(ChatFormatting.RED)
            );
            return true; // Still prevent attack
        }

        // If there was a previously focused entity, remove its glowing effect
        LivingEntity previousEntity = MinecoloniesTalkingCitizens.activeEntity.get(playerId);
        if (previousEntity != null && previousEntity.isAlive()) {
            previousEntity.removeEffect(MobEffects.GLOWING);
        }

        // Set citizen as active entity and add glowing effect
        MinecoloniesTalkingCitizens.activeEntity.put(playerId, citizen);
        citizen.addEffect(new MobEffectInstance(MobEffects.GLOWING, -1, 0, false, false));

        // Use the centralized startConversation method
        MinecoloniesTalkingCitizens.startConversation(serverPlayer, citizen);
        
        serverPlayer.sendSystemMessage(
                Component.literal("Started conversation with " + citizen.getName().getString())
                        .withStyle(ChatFormatting.GREEN)
        );

        return true; // Prevent normal attack behavior
    }

    private void handleCitizenInteraction(ServerPlayer player, UUID playerId, AbstractEntityCitizen citizen, UUID citizenId) {
        // If there was a previously focused entity, remove its glowing effect
        LivingEntity previousEntity = MinecoloniesTalkingCitizens.activeEntity.get(playerId);
        if (previousEntity != null && previousEntity.isAlive() && !previousEntity.getUUID().equals(citizenId)) {
            previousEntity.removeEffect(MobEffects.GLOWING);
        }

        // If it's a new citizen or a different one, set up a new talking manager
        if (previousEntity == null || !previousEntity.getUUID().equals(citizenId)) {
            // Use the centralized startConversation method
            MinecoloniesTalkingCitizens.startConversation(player, citizen);

            // Send feedback to player
            player.sendSystemMessage(
                    Component.literal("Now talking to " + citizen.getName().getString())
                            .withStyle(ChatFormatting.GREEN)
            );
        }
    }
}