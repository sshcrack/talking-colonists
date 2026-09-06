package me.sshcrack.mc_talking.api.examples;

import com.google.gson.JsonObject;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.conversation.CitizenActivityReservation;
import me.sshcrack.mc_talking.api.conversation.CitizenConversationRules;
import me.sshcrack.mc_talking.api.conversation.CitizenConversationService;
import me.sshcrack.mc_talking.api.conversation.ControlledConversationSession;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.api.memory.CitizenMemoryService;
import me.sshcrack.mc_talking.api.pregen.PregenerationKind;
import me.sshcrack.mc_talking.api.pregen.PregenerationPromptService;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.api.prompt.PromptContribution;
import me.sshcrack.mc_talking.api.prompt.PromptTarget;
import me.sshcrack.mc_talking.api.tool.AiTool;
import me.sshcrack.mc_talking.api.tool.AiToolContext;
import me.sshcrack.mc_talking.api.tool.AiToolRegistry;
import me.sshcrack.mc_talking.api.tool.AiToolScope;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Compile-only examples kept in tests so public addon snippets cannot silently rot. */
final class AddonApiCompileExample {
    private AddonApiCompileExample() {
    }

    static List<AutoCloseable> registerColonistErrandsStyleHooks(
            Predicate<AbstractEntityCitizen> onMilitaryDuty
    ) {
        List<AutoCloseable> registrations = new ArrayList<>();

        registrations.add(AiToolRegistry.register("example_addon", "come_here", new AiTool() {
            @Override
            public String description() {
                return "Ask this citizen to come to the player.";
            }

            @Override
            public AiToolScope scope() {
                return AiToolScope.PLAYER_CONVERSATION;
            }

            @Override
            public boolean canExecute(AiToolContext context) {
                ServerPlayer player = context.player();
                return player != null
                        && player.getUUID().equals(context.colony().getPermissions().getOwner());
            }

            @Override
            public JsonObject execute(AiToolContext context, JsonObject parameters) {
                ServerPlayer player = context.requirePlayer();
                JsonObject result = new JsonObject();
                result.addProperty("accepted", true);
                result.addProperty("player", player.getName().getString());
                return result;
            }
        }));

        registrations.add(CitizenPromptService.registerContributor(
                "example_addon:verified_state",
                100,
                (view, target) -> target == PromptTarget.CITIZEN_ROLEPLAY
                        || target == PromptTarget.SYSTEM_CONTROLLED_ROLEPLAY
                        ? List.of(PromptContribution.observation(
                                "Verified addon state",
                                "The addon's current, server-verified state for this citizen goes here."))
                        : List.of()));

        registrations.add(CitizenConversationRules.registerSpeechPolicy(
                "example_addon:military_duty",
                100,
                (citizen, kind) -> kind == ConversationKind.PLAYER || !onMilitaryDuty.test(citizen)));

        registrations.add(CitizenConversationRules.registerUrgencyModifier(
                "example_addon:military_urgency",
                100,
                (citizen, weight) -> onMilitaryDuty.test(citizen) ? 0.0 : weight));

        registrations.add(PregenerationPromptService.registerModifier(
                "example_addon:greeting_rules",
                100,
                (context, prompt) -> context.kind() == PregenerationKind.PLAYER_GREETING
                        ? prompt + " Keep this greeting generic and reusable."
                        : prompt));

        return registrations;
    }

    static Optional<CitizenActivityReservation> startErrand(AbstractEntityCitizen citizen) {
        return CitizenConversationService.reserveActivity(citizen, "example_addon:errand");
    }

    static void confirmedOutcome(AbstractEntityCitizen citizen) {
        CitizenMemoryService.addEvent(citizen, "I completed the delivery I promised to make.");
    }

    static ControlledConversationSession openMeeting(
            MinecraftServer server,
            List<AbstractEntityCitizen> attendees,
            AbstractEntityCitizen selectedSpeaker
    ) {
        ControlledConversationSession meeting = CitizenConversationService.createControlledSession(
                server, attendees, "Discuss the colony's food supply and defenses.");

        // Colony Meetings owns navigation. Call requestTurn only after selectedSpeaker has arrived.
        meeting.requestTurn(selectedSpeaker, "Give your view on the first agenda item.");
        return meeting;
    }

    static void playerQuestionThenCitizenTurn(
            ControlledConversationSession meeting,
            ServerPlayer player,
            AbstractEntityCitizen speaker
    ) {
        meeting.addPlayerStatement(player, "What should we improve first?");
        meeting.requestTurn(speaker, "Answer the player's question using the meeting context.");
    }
}
