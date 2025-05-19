package me.sshcrack.mc_talking.item;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.Config;
import me.sshcrack.mc_talking.MinecoloniesTalkingCitizens;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

public class CitizenTalkingDevice extends Item {

    public CitizenTalkingDevice() {
        super(new Item.Properties().stacksTo(1).component(DataComponents.CUSTOM_DATA, CustomData.EMPTY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        if (stack.get(DataComponents.CUSTOM_DATA) == null)
            return false;

        var comp = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        if (!comp.contains("talkingPlayer"))
            return false;

        var uuid = comp.getUUID("talkingPlayer");
        return MinecoloniesTalkingCitizens.activeEntity.containsKey(uuid);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.mc_talking.talking_device.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        if (Math.random() <= 0.25) {
            var comp = stack.get(DataComponents.CUSTOM_DATA).copyTag();
            if (!comp.contains("talkingPlayer")) {
                stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(0));
                return;
            }

            var uuid = comp.getUUID("talkingPlayer");
            var isActive = MinecoloniesTalkingCitizens.activeEntity.containsKey(uuid);
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(isActive ? 1 : 0));
        }
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

        var comp = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        comp.putUUID("talkingPlayer", playerId);

        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(comp));

        serverPlayer.sendSystemMessage(
                Component.literal("Started conversation with " + citizen.getName().getString())
                        .withStyle(ChatFormatting.GREEN)
        );

        return true; // Prevent normal attack behavior
    }
}