package me.sshcrack.mc_talking.api.examples;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.ApiFeature;
import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import me.sshcrack.mc_talking.api.colony.AddonColonyEvent;
import me.sshcrack.mc_talking.api.colony.ColonyEventService;
import me.sshcrack.mc_talking.api.conversation.CitizenConversationRules;
import me.sshcrack.mc_talking.api.conversation.CitizenConversationService;
import me.sshcrack.mc_talking.api.conversation.ControlledConversationOptions;
import me.sshcrack.mc_talking.api.conversation.ControlledConversationSession;
import me.sshcrack.mc_talking.api.conversation.ControlledSessionRejectedException;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.api.conversation.ConversationUtteranceEvent;
import me.sshcrack.mc_talking.api.conversation.PlayerConversationOptions;
import me.sshcrack.mc_talking.api.conversation.PlayerTextResult;
import me.sshcrack.mc_talking.api.guide.AddonGuide;
import me.sshcrack.mc_talking.api.guide.AddonGuideService;
import me.sshcrack.mc_talking.api.intro.CitizenIntroductionService;
import me.sshcrack.mc_talking.api.intro.Introduction;
import me.sshcrack.mc_talking.api.memory.BroadcastPublishResult;
import me.sshcrack.mc_talking.api.memory.BroadcastRequest;
import me.sshcrack.mc_talking.api.memory.BroadcastSource;
import me.sshcrack.mc_talking.api.memory.CitizenMemoryService;
import me.sshcrack.mc_talking.api.provider.ProviderBudgetService;
import me.sshcrack.mc_talking.api.provider.ProviderBudgetView;
import me.sshcrack.mc_talking.api.provider.ProviderQuotaState;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.api.speech.PlayerSpeechCapture;
import me.sshcrack.mc_talking.api.speech.SpeechCaptureResult;
import me.sshcrack.mc_talking.api.text.CitizenTextService;
import me.sshcrack.mc_talking.api.text.TextRequest;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Compile-checked usage of every API 2.1 feature (roadmap A11). Each entry point is guarded by
 * {@link TalkingColonistsApi#supports(ApiFeature)} so the same addon also runs on 2.0.x runtimes
 * (see the Feature detection section in {@code docs/addon-api.md}).
 */
final class Api21FeaturesExample {
    private Api21FeaturesExample() {
    }

    /** A1: a notice board publishes a colony broadcast. */
    static boolean announceMarketDay(IColony colony) {
        if (!TalkingColonistsApi.supports(ApiFeature.BROADCAST_PUBLISHING)) return false;
        BroadcastPublishResult result = CitizenMemoryService.publishBroadcast(colony, BroadcastRequest.immediate(
                BroadcastSource.addon("market", "Market board"), "Market day tomorrow at the town square."));
        return result.isPublished();
    }

    /** A2: record an addon event and follow every event. */
    static AddonRegistration followColonyEvents(IColony colony) {
        if (!TalkingColonistsApi.supports(ApiFeature.COLONY_EVENTS)) return null;
        ColonyEventService.record(colony, new AddonColonyEvent("market", "stall_opened", "A new fish stall opened."));
        return ColonyEventService.registerListener("market:gazette", 0,
                (eventColony, event) -> { /* write the event into a newspaper */ });
    }

    /** A3: a letter written in a citizen's voice. */
    static void writeThankYouLetter(AbstractEntityCitizen citizen, Consumer<String> deliver) {
        if (!TalkingColonistsApi.supports(ApiFeature.TEXT_GENERATION)) return;
        CitizenTextService.generate(citizen, TextRequest.of("postal:letter", "Thank Steve for the new well."))
                .thenAccept(result -> {
                    if (result.isSuccess()) deliver.accept(result.text());
                });
    }

    /** A4: a typed line and a game-event note in the player's conversation. */
    static boolean typeToCitizen(ServerPlayer player, AbstractEntityCitizen citizen, String line) {
        if (!TalkingColonistsApi.supports(ApiFeature.PLAYER_TEXT_INPUT)) return false;
        PlayerTextResult sent = CitizenConversationService.sendPlayerText(player, citizen, line);
        CitizenConversationService.addContext(player, citizen, "The player just handed you the deed.");
        return sent.isDelivered();
    }

    /** A5: every finished player utterance. */
    static AddonRegistration listenForTestimony(Consumer<String> record) {
        if (!TalkingColonistsApi.supports(ApiFeature.UTTERANCE_EVENTS)) return null;
        return CitizenConversationService.registerUtteranceListener("court:testimony", 0, event -> {
            if (event.speaker() == ConversationUtteranceEvent.Speaker.PLAYER) record.accept(event.text());
        });
    }

    /** A6: a scoped quest-giver conversation. */
    static boolean startQuestTalk(ServerPlayer player, AbstractEntityCitizen citizen) {
        if (!TalkingColonistsApi.supports(ApiFeature.PLAYER_CONVERSATION_OPTIONS)) return false;
        PlayerConversationOptions options = PlayerConversationOptions.defaults()
                .withAgenda("Ask the player to find your lost cat.")
                .withPurpose("quests:lost_cat");
        return CitizenConversationService.startPlayerConversation(player, citizen, options).started();
    }

    /** A7: only start optional chatter while capacity and quota allow it. */
    static boolean canAffordAmbientChatter() {
        if (!TalkingColonistsApi.supports(ApiFeature.PROVIDER_BUDGET)) return true;
        ProviderBudgetView budget = ProviderBudgetService.snapshot();
        return budget.canStartConversation()
                && budget.models().stream().allMatch(model -> model.state() == ProviderQuotaState.OK);
    }

    /** A8: tavern guests may greet players. */
    static AddonRegistration letVisitorsGreet() {
        if (!TalkingColonistsApi.supports(ApiFeature.VISITOR_SPEAKERS)) return null;
        return CitizenConversationRules.registerVisitorPolicy("tavern:guests", 0,
                (visitor, kind) -> kind == ConversationKind.PLAYER);
    }

    /** A9: a meeting between citizens of two allied colonies. */
    static ControlledConversationSession startEmbassyMeeting(MinecraftServer server, List<AbstractEntityCitizen> delegates) {
        if (!TalkingColonistsApi.supports(ApiFeature.CROSS_COLONY_SESSIONS)) return null;
        try {
            return CitizenConversationService.createControlledSession(server, delegates,
                    "Negotiate a trade route", ControlledConversationOptions.noAddonTools());
        } catch (ControlledSessionRejectedException rejected) {
            return null; // e.g. Reason.CROSS_DIMENSION
        }
    }

    /** A10: a loudspeaker item turns the player's words into text. */
    static void useLoudspeaker(ServerPlayer player, Consumer<String> announce) {
        if (!TalkingColonistsApi.supports(ApiFeature.PLAYER_SPEECH_CAPTURE)) return;
        PlayerSpeechCapture.capture(player, Duration.ofSeconds(15)).thenAccept(result -> {
            if (result.status() == SpeechCaptureResult.Status.TRANSCRIBED) announce.accept(result.transcript());
        });
    }

    /** A market addon explains itself in the Colony Handbook, and citizens can explain it too. */
    static AddonRegistration explainTheMarket() {
        if (!TalkingColonistsApi.supports(ApiFeature.ADDON_GUIDES)) return null;
        return AddonGuideService.register(new AddonGuide("market:stalls", "Market Day",
                "Once a week citizens set up stalls and trade what they made.",
                List.of("Craft a Market Stall and place it in the colony.",
                        "On market day, right-click a stall to see what is on offer."),
                List.of("Operators: /market start opens the market now.")));
    }

    /** A citizen tells each player about market day once, from the colony's third day on. */
    static AddonRegistration introduceTheMarket() {
        if (!TalkingColonistsApi.supports(ApiFeature.INTRODUCTIONS)) return null;
        return CitizenIntroductionService.register(new Introduction("market:stalls", "market day",
                "Tell them that once a week citizens set up stalls in the square, and that they are welcome to come.",
                "market:stalls", (player, colony) -> colony.getDay() >= 3));
    }
}
