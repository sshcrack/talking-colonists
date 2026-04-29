package me.sshcrack.mc_talking.conversations.memory;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.misc.GeminiFlash;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenRelationshipChangeType;
import me.sshcrack.mc_talking.conversations.memory.gson.GsonMemoryResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

public class CitizenMemoryGenerator extends Thread {
    private static final String PROMPT = ("""
            Extract persistent memory from the following conversation.

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

    public CitizenMemoryGenerator(String input, List<AbstractEntityCitizen> participants) {
        this.conversation = input;
        this.participants = participants;
    }

    @Override
    public void run() {
        McTalking.LOGGER.debug("Starting memory generation for conversation: {}", conversation);
        String apiKey = CONFIG.geminiApiKey.get();
        String memoryString = "";
        try {
            memoryString = GeminiFlash.sendSimpleFlashRequest(apiKey, McTalkingConfig.FLASH_MODEL, PROMPT, conversation);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            McTalking.LOGGER.debug("Memory generation thread was interrupted for conversation: {}", conversation);
            return;
        } catch (UnexpectedResponseException | IOException e) {
            McTalking.LOGGER.error("Failed to generate memory for conversation: {}", conversation, e);
            throw new RuntimeException(e);
        }

        var json = GsonMemoryResponse.GSON.fromJson(memoryString, GsonMemoryResponse.class);
        if (json == null) {
            McTalking.LOGGER.warn("Failed to parse memory response JSON: {}", memoryString);
            return;
        }

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
                    continue;
                }

                memory.addRelationshipChange(target.getCitizenData(), relationship.type, relationship.change);
            }
        }

        McTalking.LOGGER.info("Updated memories for conversation with {} participants", participants.size());
    }

    public static void addAndGenerateMemory(String conversation, List<AbstractEntityCitizen> citizens) {
        new CitizenMemoryGenerator(conversation, citizens).start();
    }
}
