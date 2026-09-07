package me.sshcrack.mc_talking.api.examples;

import com.google.gson.JsonObject;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.context.CitizenContextService;
import me.sshcrack.mc_talking.api.conversation.CitizenActivityReservation;
import me.sshcrack.mc_talking.api.conversation.CitizenConversationRules;
import me.sshcrack.mc_talking.api.conversation.CitizenConversationService;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.api.conversation.ConversationLifecycleEvent;
import me.sshcrack.mc_talking.api.conversation.ConversationStartResult;
import me.sshcrack.mc_talking.api.memory.AddonConfirmedOutcome;
import me.sshcrack.mc_talking.api.memory.AddonMemoryWriteResult;
import me.sshcrack.mc_talking.api.memory.CitizenMemoryService;
import me.sshcrack.mc_talking.api.memory.CitizenRelationshipDimension;
import me.sshcrack.mc_talking.api.memory.ConfirmedRelationshipChange;
import me.sshcrack.mc_talking.api.prompt.view.ObservationState;
import me.sshcrack.mc_talking.api.pregen.PregenerationKind;
import me.sshcrack.mc_talking.api.pregen.PregenerationPromptService;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.api.prompt.PromptContribution;
import me.sshcrack.mc_talking.api.prompt.PromptTarget;
import me.sshcrack.mc_talking.api.tool.AiCommandTool;
import me.sshcrack.mc_talking.api.tool.AiQueryTool;
import me.sshcrack.mc_talking.api.tool.AiToolContext;
import me.sshcrack.mc_talking.api.tool.AiToolParameter;
import me.sshcrack.mc_talking.api.tool.AiToolPermission;
import me.sshcrack.mc_talking.api.tool.AiToolRegistry;
import me.sshcrack.mc_talking.api.tool.AiToolScope;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/** Compile-only examples kept in tests so public addon snippets cannot silently rot. */
final class AddonApiCompileExample {
    private AddonApiCompileExample() {
    }

    static List<AutoCloseable> registerColonistErrandsStyleHooks(
            Predicate<AbstractEntityCitizen> onMilitaryDuty
    ) {
        List<AutoCloseable> registrations = new ArrayList<>();

        registrations.add(AiToolRegistry.register("example_addon", "current_destination", new AiQueryTool() {
            @Override
            public String description() {
                return "Read the destination currently requested by the addon.";
            }

            @Override
            public AiToolParameter parameters() {
                return AiToolParameter.object(Map.of());
            }

            @Override
            public JsonObject executeQuery(AiToolContext context, JsonObject parameters) {
                JsonObject result = new JsonObject();
                result.addProperty("citizen", context.citizen().getName().getString());
                result.addProperty("sessionId", context.sessionId().toString());
                return result;
            }
        }));

        registrations.add(AiToolRegistry.register("example_addon", "come_here", new AiCommandTool() {
            @Override
            public String description() {
                return "Ask this citizen to come to the player.";
            }

            @Override
            public AiToolScope scope() {
                return AiToolScope.PLAYER_CONVERSATION;
            }

            @Override
            public AiToolPermission permission() {
                return AiToolPermission.RIGHTCLICK_ENTITY;
            }

            @Override
            public AiToolParameter parameters() {
                return AiToolParameter.object(Map.of(
                        "destination", AiToolParameter.string(true)
                ));
            }

            @Override
            public CompletionStage<JsonObject> executeCommand(AiToolContext context, JsonObject parameters) {
                // Long work may finish later. Only the world-changing continuation is marshalled
                // back to the Minecraft server thread. Core immediately returns an operation ID.
                return CompletableFuture.supplyAsync(() -> parameters.get("destination").getAsString())
                        .thenCompose(destination -> context.supplyOnServerThread(() -> {
                            ServerPlayer player = context.requirePlayer();
                            // Addon-owned navigation/world mutation would begin here.
                            JsonObject result = new JsonObject();
                            result.addProperty("destination", destination);
                            result.addProperty("requestedBy", player.getUUID().toString());
                            return result;
                        }));
            }
        }));

        registrations.add(CitizenPromptService.registerContributor(
                "example_addon:verified_state",
                100,
                context -> context.target() == PromptTarget.CITIZEN_ROLEPLAY
                        || context.target() == PromptTarget.SYSTEM_CONTROLLED_ROLEPLAY
                        ? List.of(PromptContribution.observation(
                                "example_addon:verified_state",
                                "Verified addon state",
                                "Current addon context for " + context.view().identity().name()
                                        + "; core activity=" + context.view().activity().category()))
                        : List.of()));

        registrations.add(CitizenPromptService.registerContributor(
                "example_addon:meeting_agenda",
                110,
                context -> context.session().agenda() == null
                        ? List.of()
                        : List.of(PromptContribution.instruction(
                                "example_addon:meeting_agenda",
                                "Meeting agenda",
                                "Keep this turn relevant to: " + context.session().agenda()))));

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

    static AddonMemoryWriteResult confirmedOutcome(
            AbstractEntityCitizen citizen,
            ServerPlayer player,
            String deliveryId
    ) {
        return CitizenMemoryService.confirmOutcome(citizen, new AddonConfirmedOutcome(
                "example_addon:deliveries",
                deliveryId,
                "The tracked delivery was completed.",
                player.getUUID(),
                List.of("The tracked delivery is fulfilled."),
                List.of(new ConfirmedRelationshipChange(
                        player.getUUID(), CitizenRelationshipDimension.TRUST, 0.1f))
        ));
    }

    static Optional<Double> currentHealth(AbstractEntityCitizen citizen) {
        var health = CitizenContextService.snapshot(citizen).verifiedFacts().healthPercent();
        return health.state() == ObservationState.CURRENT
                ? Optional.of(health.value())
                : Optional.empty();
    }

    static void speakThenContinue(AbstractEntityCitizen citizen) {
        CitizenConversationService.requestAmbientLine(citizen, "Thank the courier for the delivery.")
                .thenAccept(result -> {
                    if (result.completed()) {
                        // Continue addon gameplay after the audible line actually finished.
                    }
                });
    }

}
