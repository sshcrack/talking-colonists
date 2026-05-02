package me.sshcrack.mc_talking.conversations.memory;

import com.google.gson.JsonSyntaxException;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.misc.GeminiFlash;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenRelationshipChangeType;
import me.sshcrack.mc_talking.conversations.memory.gson.GsonMemoryResponse;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

/**
 * Generates and saves memories for a citizen after a conversation with a player.
 * Runs on a background thread to avoid blocking the server tick.
 */
public class PlayerConversationMemoryGenerator extends Thread {

    private static final String PROMPT_TEMPLATE = """
            Extract persistent memories for citizen %s from their conversation with player %s (colony rank: %s).

            The following is a partial transcript of what the citizen said during the conversation (the player's voice is not captured):
            ---
            %s
            ---
            %s

            Return ONLY valid JSON.

            Rules:
            - Write facts and events in first person from the citizen's perspective
            - Only include meaningful information (ignore small talk)
            - Relationship changes should reflect how the citizen's feelings toward the player changed, as a float between -1.0 and 1.0
            - Allowed relationship types: %s

            Format:
            {
              "citizens": [
                {
                  "name": "%s",
                  "memories": {
                    "relationships": [
                      {"target": "%s", "type": "trust", "change": 0.1}
                    ],
                    "facts": [
                      "Player %s is the colony manager"
                    ],
                    "events": [
                      "Had a conversation with %s about colony supplies"
                    ]
                  }
                }
              ]
            }
            """;

    private final AbstractEntityCitizen citizen;
    private final UUID playerUuid;
    private final String playerName;
    private final String playerRank;
    private final String transcript;
    private final MinecraftServer server;

    private PlayerConversationMemoryGenerator(
            AbstractEntityCitizen citizen,
            UUID playerUuid,
            String playerName,
            String playerRank,
            String transcript,
            MinecraftServer server) {
        this.citizen = citizen;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.playerRank = playerRank;
        this.transcript = transcript;
        this.server = server;
        setDaemon(true);
        setName("mc-talking-player-memory-" + citizen.getStringUUID());
    }

    @Override
    public void run() {
        String citizenName = citizen.getCitizenData().getName();
        McTalking.LOGGER.debug("[PlayerMemory] Generating memories for {} after conversation with {}", citizenName, playerName);

        String apiKey = CONFIG.geminiApiKey.get();
        String allowedTypes = Arrays.stream(CitizenRelationshipChangeType.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));

        String transcriptSection = transcript.isBlank()
                ? "(No transcript available — the conversation was in audio-only mode.)"
                : transcript;

        String contextNote = transcript.isBlank()
                ? "Since no transcript is available, generate plausible memories based on the citizen's known state and the fact that they spoke with this player."
                : "";

        String prompt = String.format(PROMPT_TEMPLATE,
                citizenName, playerName, playerRank,
                transcriptSection, contextNote,
                allowedTypes,
                citizenName, playerName,
                playerName, playerName);

        String responseJson;
        try {
            responseJson = GeminiFlash.sendSimpleFlashRequest(McTalkingConfig.FLASH_MODEL, apiKey, prompt, "Generate the memory JSON now.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            McTalking.LOGGER.debug("[PlayerMemory] Thread interrupted for citizen {}", citizenName);
            return;
        } catch (UnexpectedResponseException | IOException e) {
            McTalking.LOGGER.error("[PlayerMemory] Failed to generate player memories for citizen {}", citizenName, e);
            return;
        }

        GsonMemoryResponse response;
        try {
            response = GsonMemoryResponse.GSON.fromJson(responseJson, GsonMemoryResponse.class);
        } catch (JsonSyntaxException e) {
            McTalking.LOGGER.warn("[PlayerMemory] Failed to parse memory JSON for citizen {}: {}", citizenName, responseJson);
            return;
        }

        server.execute(() -> saveMemories(response, citizenName));
        McTalking.LOGGER.info("[PlayerMemory] Saved player-interaction memories for citizen {}", citizenName);
    }

    private void saveMemories(GsonMemoryResponse response, String citizenName) {
        for (var gsonCitizen : response.citizens) {
            if (!gsonCitizen.name.equals(citizenName)) continue;

            var data = (CitizenDataMemoryExtended) citizen.getCitizenData();
            var memory = data.mc_talking$getOrInitializeMemory();

            for (String fact : gsonCitizen.memories.facts) {
                memory.addFact(fact);
            }
            for (String event : gsonCitizen.memories.events) {
                memory.addEvent(event);
            }
            for (var rel : gsonCitizen.memories.relationships) {
                if (rel.target.equals(playerName) && rel.type != null) {
                    memory.addRelationshipChange(playerUuid, rel.type, rel.change);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Static lifecycle
    // -------------------------------------------------------------------------

    private static final List<PlayerConversationMemoryGenerator> activeGenerators = new CopyOnWriteArrayList<>();

    /**
     * Creates and starts a memory generation thread for a completed player conversation.
     *
     * @param citizen    the citizen who had the conversation
     * @param playerUuid the UUID of the player
     * @param playerName the display name of the player
     * @param playerRank the colony rank name of the player (e.g. "manager", "visitor")
     * @param transcript the partial transcript of the citizen's speech (may be blank for audio-only)
     * @param server     the Minecraft server for thread-safe NBT writes
     */
    public static void generateAndSave(
            AbstractEntityCitizen citizen,
            UUID playerUuid,
            String playerName,
            String playerRank,
            String transcript,
            MinecraftServer server) {
        var generator = new PlayerConversationMemoryGenerator(
                citizen, playerUuid, playerName, playerRank, transcript, server);
        activeGenerators.add(generator);
        generator.setUncaughtExceptionHandler((t, e) -> {
            McTalking.LOGGER.error("[PlayerMemory] Uncaught exception in memory thread", e);
            activeGenerators.remove(generator);
        });
        generator.start();
    }

    /**
     * Interrupts all running generators. Called on server stop.
     */
    public static void stopAllGenerators() {
        for (var generator : activeGenerators) {
            generator.interrupt();
        }
        activeGenerators.clear();
    }
}
