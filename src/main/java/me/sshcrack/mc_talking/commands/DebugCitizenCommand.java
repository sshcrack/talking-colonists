package me.sshcrack.mc_talking.commands;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.config.PersonalityArchetype;
import me.sshcrack.mc_talking.duck.AbstractEntityCitizenAiStatusProvider;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.duck.CitizenDataPersonalityExtended;
import me.sshcrack.mc_talking.manager.GeminiWsClient;
import me.sshcrack.mc_talking.network.AiStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class DebugCitizenCommand {

    private static final SimpleCommandExceptionType NOT_A_CITIZEN =
            new SimpleCommandExceptionType(Component.translatable("mc_talking.debug.not_citizen"));

    private DebugCitizenCommand() {
    }

    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("citizen")
                .then(Commands.argument("target", EntityArgument.entity())
                        .executes(ctx -> execute(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))));
    }

    private static int execute(CommandSourceStack source, net.minecraft.world.entity.Entity target) throws CommandSyntaxException {
        if (!(target instanceof AbstractEntityCitizen citizen)) {
            throw NOT_A_CITIZEN.create();
        }

        ICitizenData data = citizen.getCitizenData();
        if (data == null) {
            source.sendFailure(Component.translatable("mc_talking.debug.no_citizen_data"));
            return 0;
        }

        UUID citizenId = citizen.getUUID();
        AiStatus aiStatus = ((AbstractEntityCitizenAiStatusProvider) citizen).mc_talking$getAiStatus();
        boolean isBusy = ConversationManager.isCitizenBusy(citizen);
        boolean onCooldown = ConversationManager.isCitizenOnCooldown(citizen);
        UUID playerPartner = ConversationManager.getPlayerForEntity(citizenId);
        GeminiWsClient client = ConversationManager.getClientForEntity(citizenId);

        var personalityExt = (CitizenDataPersonalityExtended) data;
        PersonalityArchetype archetype = personalityExt.mc_talking$getPersonality();
        String customPersonality = personalityExt.mc_talking$getCustomPersonality();

        String jobName = data.getJob() != null
                ? Component.translatable(data.getJob().getJobRegistryEntry().getTranslationKey()).getString()
                : "unemployed";
        String colonyName = data.getColony() != null ? data.getColony().getName() : "none";
        int colonyId = data.getColony() != null ? data.getColony().getID() : -1;

        String partnerName = null;
        if (playerPartner != null) {
            var player = source.getServer().getPlayerList().getPlayer(playerPartner);
            if (player != null) partnerName = player.getName().getString();
        }

        String cooldownStr = "none";
        Long lastEnd = ConversationManager.getLastSessionEndTimes().get(citizenId);
        if (lastEnd != null) {
            cooldownStr = McTalkingDebugCommand.cooldownRemaining(lastEnd,
                    me.sshcrack.mc_talking.config.McTalkingConfig.INSTANCE.instance().citizenCooldownSeconds);
        }

        String personalityStr;
        if (archetype != null) personalityStr = archetype.name();
        else if (customPersonality != null) personalityStr = "custom: " + customPersonality.substring(0, Math.min(40, customPersonality.length())) + "…";
        else personalityStr = "none";

        String sessionType;
        if (client == null) sessionType = "none";
        else if (client instanceof me.sshcrack.mc_talking.manager.CitizenWsClient cws && cws.isMumbling()) sessionType = "mumble";
        else sessionType = "player";

        String sessionDuration = client != null ? McTalkingDebugCommand.formatDuration(client.getSessionStartTimeMs()) : "—";

        var doubleOpt = data.getEntity();
        Double healthPct = doubleOpt.isPresent()
                ? (doubleOpt.get().getHealth() / Math.max(1.0f, doubleOpt.get().getMaxHealth())) * 100.0
                : null;

        final String fPartnerName = partnerName;
        final String fSessionType = sessionType;
        final String fSessionDuration = sessionDuration;
        final String fCooldownStr = cooldownStr;
        final String fPersonalityStr = personalityStr;
        final Double fHealthPct = healthPct;

        source.sendSuccess(() -> {
            var msg = Component.literal("")
                    .append(Component.translatable("mc_talking.debug.citizen_header", data.getName())
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal("\n"))
                    .append(field("UUID", citizenId.toString()))
                    .append(field("Colony", colonyName + " (#" + colonyId + ")"))
                    .append(field("Job", jobName))
                    .append(field("Health", fHealthPct != null ? String.format("%.1f%%", fHealthPct) : "not loaded"))
                    .append(field("Saturation", String.format("%.1f", data.getSaturation())))
                    .append(field("AI Status", aiStatus.name()))
                    .append(field("Personality", fPersonalityStr))
                    .append(field("Active Session", fSessionType))
                    .append(field("Duration", fSessionDuration));

            if (fPartnerName != null) {
                msg.append(field("Talking to", fPartnerName));
            }
            msg.append(field("Cooldown", fCooldownStr));
            msg.append(field("isChild", String.valueOf(data.isChild())));
            msg.append(field("isSick", String.valueOf(data.getCitizenDiseaseHandler().isSick())));
            msg.append(field("isHomeless", String.valueOf(data.getHomeBuilding() == null)));

            return msg;
        }, false);
        return 1;
    }

    private static Component field(String label, String value) {
        return Component.literal("")
                .append(Component.literal("  §7" + label + ": §f").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n"));
    }
}
