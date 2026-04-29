package me.sshcrack.mc_talking.conversations.memory;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.misc.GeminiFlash;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenRelationshipChangeType;
import me.sshcrack.mc_talking.conversations.memory.gson.GsonMemoryResponse;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

public class CitizenMemoryGenerator extends Thread {
    private static final String PROMPT = ("""
            Extract persistent memories from the following conversation.

            Return ONLY valid JSON.

            Rules:
            - Only include information worth remembering long-term
            - Ignore small talk
            - Prefer facts, relationships, opinions, and events
            - Write from the perspective of each participant
            - Be concise and structured
            - Allowed types of relationship changes:  %s
            - The change in the relationships should be a float between -1.0 and 1.0, where negative values indicate a worsening relationship and positive values indicate an improving relationship.

            Format:
            {
              "citizens": [
                {
                  "name": "Tomas Reed",
                  "memories": {
                    "relationships": [
                      {"target": "Anna", "type": "trust", "change": 0.2}
                    ],
                    "facts": [
                      "Anna is struggling with food supplies"
                    ],
                    "events": [
                      "Argued with Anna about resource management"
                    ]
                  }
                }
              ]
            }
            """).formatted(Arrays.stream(CitizenRelationshipChangeType.values()).map(Enum::toString).collect(Collectors.joining(", ")));

    private final String conversation;
    private final List<AbstractEntityCitizen> participants;
    private final MinecraftServer server;

    public CitizenMemoryGenerator(String input, List<AbstractEntityCitizen> participants, MinecraftServer server) {
        this.conversation = input;
        this.participants = Collections.unmodifiableList(participants);
        this.server = server;
    }

    @Override
    public void run() {
        McTalking.LOGGER.debug("Starting memories generation for conversation: {}", conversation);
        String apiKey = CONFIG.geminiApiKey.get();
        String memoryString;
        try {
            memoryString = GeminiFlash.sendSimpleFlashRequest(McTalkingConfig.FLASH_MODEL, apiKey, PROMPT, conversation);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            McTalking.LOGGER.debug("Memory generation thread was interrupted for conversation: {}", conversation);
            return;
        } catch (UnexpectedResponseException | IOException e) {
            McTalking.LOGGER.error("Failed to generate memories for conversation: {}", conversation, e);
            throw new RuntimeException(e);
        }

        var json = GsonMemoryResponse.GSON.fromJson(memoryString, GsonMemoryResponse.class);
        if (json == null) {
            McTalking.LOGGER.warn("Failed to parse memories response JSON: {}", memoryString);
            return;
        }

        server.executeBlocking(() -> minecraftSaveMemoryRun(json));

        McTalking.LOGGER.info("Updated memories for conversation with {} participants", participants.size());
    }

    private void minecraftSaveMemoryRun(GsonMemoryResponse json) {
        for (var gsonCitizens : json.citizens) {
            var citizen = participants.stream()
                    .filter(c -> c.getName().getString().equals(gsonCitizens.name))
                    .findFirst()
                    .orElse(null);

            if (citizen == null) {
                McTalking.LOGGER.warn("Memory response contains unknown citizen: {}", gsonCitizens.name);
                continue;
            }

            var data = (CitizenDataMemoryExtended) citizen.getCitizenData();
            var memory = data.mc_talking$getOrInitializeMemory();

            var gsonMemory = gsonCitizens.memories;
            for (String fact : gsonMemory.facts) {
                memory.addFact(fact);
            }

            for (String event : gsonMemory.events) {
                memory.addEvent(event);
            }

            for (GsonMemoryResponse.GsonRelationshipMemory relationship : gsonMemory.relationships) {
                var target = participants.stream()
                        .filter(c -> c.getName().getString().equals(relationship.target))
                        .findFirst()
                        .orElse(null);
                if (target == null) {
                    McTalking.LOGGER.warn("Memory response contains relationship change with unknown target citizen: {}", relationship.target);
                } else if (relationship.type == null) {
                    McTalking.LOGGER.warn("Memory response contains relationship change with null type for target citizen: {}", relationship.target);
                } else {
                    memory.addRelationshipChange(target.getUUID(), relationship.type, relationship.change);
                }
            }
        }
    }

    private static final List<CitizenMemoryGenerator> activeGenerators = new CopyOnWriteArrayList<>();

    public static void addAndGenerateMemory(String conversation, List<AbstractEntityCitizen> citizens, MinecraftServer server) {
        var generator = new CitizenMemoryGenerator(conversation, citizens, server);
        activeGenerators.add(generator);
        generator.start();
    }

    public static void stopAllGenerators() {
        for (var generator : activeGenerators) {
            generator.interrupt();
        }

        activeGenerators.clear();
        //TODO shutdown cleanly and wait for threads to finish
    }
}
