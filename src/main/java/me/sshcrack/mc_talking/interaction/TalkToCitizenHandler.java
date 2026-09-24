package me.sshcrack.mc_talking.interaction;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.visitor.VisitorCitizen;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.internal.api.ConversationRuleRuntime;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalkingVoicechatPlugin;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared server-side entry point for starting/ending a direct player conversation without
 * the {@code CitizenTalkingDevice} item: the sneak+attack gesture and the "talk to citizen"
 * keybind both funnel through here.
 *
 * <p>Both callers must already have resolved and, for the keybind, re-validated the target
 * entity server-side (existence, type, distance) before calling {@link #attempt}. This class
 * only decides/executes the conversation toggle itself and sends the same player-facing
 * feedback as {@code CitizenTalkingDevice#onLeftClickEntity}.</p>
 */
public final class TalkToCitizenHandler {
    private TalkToCitizenHandler() {
    }

    /** Whether the config gate for both non-device entry points is on. */
    public static boolean isEnabled() {
        return McTalkingConfig.INSTANCE.instance().enableTalkWithoutDevice;
    }

    /**
     * Attempts to start, or toggles off, a direct player conversation with {@code citizen}.
     *
     * @param withinRange whether the caller has already verified {@code citizen} is within
     *                    the configured max conversation distance of {@code player}
     */
    public static void attempt(ServerPlayer player, AbstractEntityCitizen citizen, boolean withinRange) {
        AbstractEntityCitizen current = ConversationManager.getActiveEntityForPlayer(player.getUUID());
        boolean alreadyTalkingToThisCitizen = current != null && current.getUUID().equals(citizen.getUUID());

        boolean voicechatApiReady = McTalkingVoicechatPlugin.vcApi != null;
        boolean voicechatDisabledForPlayer = voicechatApiReady
                && McTalkingVoicechatPlugin.shouldDisableColoniesTicks(player);

        GestureConversationDecision.Outcome outcome = GestureConversationDecision.decide(
                isEnabled(),
                alreadyTalkingToThisCitizen,
                withinRange,
                citizen instanceof VisitorCitizen
                        && !ConversationRuleRuntime.addonsAllowVisitor(citizen, ConversationKind.PLAYER),
                voicechatApiReady,
                voicechatDisabledForPlayer,
                ConversationManager.canCitizenSpeak(citizen, true)
        );

        switch (outcome) {
            case FEATURE_DISABLED -> {
                // No-op: let vanilla behavior (e.g. the attack) proceed untouched.
            }
            case END_CONVERSATION -> {
                ConversationManager.endConversation(player.getUUID(), false);
                player.displayClientMessage(Component.translatable("mc_talking.conversation_ended"), true);
            }
            case TOO_FAR -> player.sendSystemMessage(
                    Component.translatable("mc_talking.talk_gesture.target_too_far").withStyle(ChatFormatting.YELLOW));
            case VISITOR_BLOCKED -> player.sendSystemMessage(
                    Component.translatable("mc_talking.talk_gesture.invalid_on_visitor").withStyle(ChatFormatting.RED));
            case VOICECHAT_UNAVAILABLE -> player.sendSystemMessage(
                    Component.translatable("mc_talking.talk_gesture.voicechat_unavailable").withStyle(ChatFormatting.RED));
            case VOICECHAT_DISABLED -> player.sendSystemMessage(
                    Component.translatable("mc_talking.talk_gesture.voicechat_disabled").withStyle(ChatFormatting.RED));
            case CANNOT_SPEAK -> player.sendSystemMessage(
                    Component.translatable("mc_talking.talk_gesture.cannot_speak").withStyle(ChatFormatting.RED));
            case ATTEMPT_START -> {
                if (ConversationManager.startPlayerConversation(player, citizen)) {
                    player.sendSystemMessage(Component.translatable("mc_talking.talk_gesture.started", citizen.getName())
                            .withStyle(ChatFormatting.GREEN));
                }
            }
        }
    }
}
