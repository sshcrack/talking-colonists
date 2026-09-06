package me.sshcrack.mc_talking.conversations.memory;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.misc.GeminiFlash;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.api.memory.CitizenRelationshipDimension;
import me.sshcrack.mc_talking.conversations.memory.gson.GsonMemoryResponse;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/** Generates persistent memories for a citizen-to-citizen transcript. */
public class CitizenMemoryGenerator extends Thread {
    private static final String PROMPT = ("""
            Extract persistent memories from the following conversation.

            Return ONLY valid JSON.

            Rules:
            - Ignore small talk
            - Prefer facts, relationships, opinions, and events. Don't include something like "I cannot work because I need spruce wood staircases"
            - Make sure each fact is unique and different. Don't include information that only regards other citizen, like "Citizen XY needs ... to continue building"
            - Write in first person and in the perspective of each participant
            - Be concise and structured
            - Allowed types of relationship changes: %s
            - Relationship changes must be finite numbers from -1.0 to 1.0.

            Format:
            {
              "citizens": [
                {
                  "name": "Tomas Reed",
                  "memories": {
                    "relationships": [
                      {"target": "Anna", "type": "trust", "change": 0.2}
                    ],
                    "facts": ["Anna is struggling with food supplies"],
                    "events": ["Argued with Anna about resource management"]
                  }
                }
              ]
            }
            """).formatted(Arrays.stream(CitizenRelationshipDimension.values())
            .map(Enum::toString).collect(Collectors.joining(", ")));

    private final String conversation;
    private final List<AbstractEntityCitizen> participants;
    private final MinecraftServer server;
    private final MemorySaveCoordinator<GsonMemoryResponse> saveCoordinator;

    public CitizenMemoryGenerator(String input, List<AbstractEntityCitizen> participants, MinecraftServer server) {
        this.conversation = input;
        this.participants = Collections.unmodifiableList(List.copyOf(participants));
        this.server = server;
        this.saveCoordinator = new MemorySaveCoordinator<>(server::execute, this::minecraftSaveMemoryRun);
        this.saveCoordinator.completion().thenAccept(result -> {
            if (result.status() == MemorySaveCoordinator.Status.SAVED) {
                McTalking.LOGGER.info("Updated memories for conversation with {} participants", participants.size());
            } else if (result.status() == MemorySaveCoordinator.Status.FAILED) {
                McTalking.LOGGER.warn("Memory generation/save failed for {} participants: {}",
                        participants.size(), result.detail());
            }
        });
        setDaemon(true);
        setName(String.format("citizen-memory-generator-%s", participants.stream()
                .map(e -> e.getCitizenData().getName()).collect(Collectors.joining("_"))));
    }

    @Override
    public void run() {
        try {
            McTalking.LOGGER.debug("Starting memory generation for {} citizen participants", participants.size());
            String apiKey = McTalkingConfig.INSTANCE.instance().geminiApiKey;
            String memoryString;
            try {
                memoryString = GeminiFlash.sendSimpleFlashRequest(McTalkingConfig.FLASH_MODEL, apiKey, PROMPT, conversation);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                saveCoordinator.cancel("memory generation interrupted");
                return;
            } catch (UnexpectedResponseException | IOException e) {
                McTalking.LOGGER.error("Failed to request citizen memories", e);
                saveCoordinator.generationFailed("memory request failed", e);
                return;
            }

            GsonMemoryResponse json;
            try {
                var names = participants.stream().map(c -> c.getName().getString()).toList();
                json = MemoryResponseParser.parse(memoryString,
                        MemoryResponseParser.ValidationContext.citizenConversation(names));
            } catch (MemoryResponseParser.ValidationException e) {
                McTalking.LOGGER.warn("Rejected invalid citizen-memory response: {}", e.getMessage());
                saveCoordinator.generationFailed("invalid model memory response", e);
                return;
            }

            saveCoordinator.generationSucceeded(json);
        } finally {
            activeGenerators.remove(this);
        }
    }

    /** Authorizes persistence once generation has also completed successfully. Idempotent. */
    public void scheduleOrSaveMemory() {
        saveCoordinator.authorizeSave();
    }

    private void minecraftSaveMemoryRun(GsonMemoryResponse json) {
        for (var gsonCitizens : json.citizens) {
            var citizen = participants.stream()
                    .filter(c -> c.getName().getString().equals(gsonCitizens.name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("validated citizen disappeared: " + gsonCitizens.name));

            var data = (CitizenDataMemoryExtended) citizen.getCitizenData();
            var memory = data.mc_talking$getOrInitializeMemory();
            var gsonMemory = gsonCitizens.memories;
            for (String fact : gsonMemory.facts) memory.addFact(fact);
            for (String event : gsonMemory.events) memory.addEvent(event);
            for (GsonMemoryResponse.GsonRelationshipMemory relationship : gsonMemory.relationships) {
                var target = participants.stream()
                        .filter(c -> c.getName().getString().equals(relationship.target))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("validated relationship target disappeared: " + relationship.target));
                memory.addRelationshipChange(target.getUUID(), relationship.type, relationship.change);
            }
        }
    }

    private static final List<CitizenMemoryGenerator> activeGenerators = new CopyOnWriteArrayList<>();

    public static CitizenMemoryGenerator addAndGenerateMemory(String conversation, List<AbstractEntityCitizen> citizens, MinecraftServer server) {
        var generator = new CitizenMemoryGenerator(conversation, citizens, server);
        activeGenerators.add(generator);
        generator.start();
        return generator;
    }

    public static void stopAllGenerators() {
        for (var generator : activeGenerators) {
            generator.saveCoordinator.cancel("server shutdown");
            generator.interrupt();
        }
        activeGenerators.clear();
    }
}
