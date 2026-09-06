package me.sshcrack.mc_talking.api.examples;

import com.google.gson.JsonObject;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.context.CitizenContextService;
import me.sshcrack.mc_talking.api.conversation.CitizenActivityReservation;
import me.sshcrack.mc_talking.api.conversation.CitizenConversationRules;
import me.sshcrack.mc_talking.api.conversation.CitizenConversationService;
import me.sshcrack.mc_talking.api.conversation.ControlledConversationSession;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.api.conversation.ConversationLifecycleEvent;
import me.sshcrack.mc_talking.api.conversation.ConversationStartResult;
import me.sshcrack.mc_talking.api.memory.CitizenMemoryService;
import me.sshcrack.mc_talking.api.memory.CitizenRelationshipDimension;
import me.sshcrack.mc_talking.api.pregen.PregenerationKind;
import me.sshcrack.mc_talking.api.pregen.PregenerationPromptService;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.api.prompt.PromptContribution;
import me.sshcrack.mc_talking.api.prompt.PromptTarget;
import me.sshcrack.mc_talking.api.tool.AiTool;
import me.sshcrack.mc_talking.api.tool.AiToolContext;
import me.sshcrack.mc_talking.api.tool.AiToolParameter;
import me.sshcrack.mc_talking.api.tool.AiToolRegistry;
import me.sshcrack.mc_talking.api.tool.AiToolScope;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            public AiToolParameter parameters() {
                return AiToolParameter.object(Map.of(
                        "destination", AiToolParameter.string(true)
                ));
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
                                "Current addon context for " + view.identity().name()
                                        + "; core activity=" + view.activity().category()))
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

        registrations.add(CitizenConversationService.registerLifecycleListener(
                "example_addon:conversation_observer",
                100,
                event -> {
                    if (event.phase() == ConversationLifecycleEvent.Phase.STARTED) {
                        // Update addon UI/state without reading ConversationManager maps.
                    }
                }));

        return registrations;
    }

    static Optional<CitizenActivityReservation> startErrand(AbstractEntityCitizen citizen) {
        return CitizenConversationService.reserveActivity(
                citizen, "example_addon:errand", Duration.ofMinutes(15));
    }

    static boolean keepLongErrandAlive(CitizenActivityReservation reservation) {
        return reservation.renew(Duration.ofMinutes(15));
    }

    static me.sshcrack.mc_talking.api.prompt.view.AIWorkerState currentWorkState(AbstractEntityCitizen citizen) {
        var snapshot = CitizenContextService.snapshot(citizen);
        return snapshot.activity().workState();
    }

    static boolean startPlayerConversation(ServerPlayer player, AbstractEntityCitizen citizen) {
        ConversationStartResult result = CitizenConversationService.startPlayerConversation(player, citizen);
        return result.started();
    }

    static void confirmedOutcome(AbstractEntityCitizen citizen, ServerPlayer player) {
        CitizenMemoryService.addEvent(citizen, "I completed the delivery I promised to make.");
        CitizenMemoryService.addRelationshipChange(
                citizen, player.getUUID(), CitizenRelationshipDimension.TRUST, 0.1f);
    }

    static void speakThenContinue(AbstractEntityCitizen citizen) {
        CitizenConversationService.requestAmbientLine(citizen, "Thank the courier for the delivery.")
                .thenAccept(result -> {
                    if (result.completed()) {
                        // Continue addon gameplay after the audible line actually finished.
                    }
                });
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

    static List<String> meetingMinuteLines(ControlledConversationSession meeting) {
        return meeting.transcript().stream()
                .map(entry -> entry.speakerName() + ": " + entry.text())
                .toList();
    }
}
