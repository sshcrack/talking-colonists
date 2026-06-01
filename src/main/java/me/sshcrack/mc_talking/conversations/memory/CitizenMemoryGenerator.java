package me.sshcrack.mc_talking.conversations.memory;

import com.google.gson.JsonSyntaxException;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.misc.GeminiFlash;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenRelationshipChangeType;
import me.sshcrack.mc_talking.conversations.memory.gson.GsonMemoryResponse;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import me.sshcrack.mc_talking.config.McTalkingConfig;

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
    private boolean shouldSaveMemory = false;
    private GsonMemoryResponse savedResponse;

    public CitizenMemoryGenerator(String input, List<AbstractEntityCitizen> participants, MinecraftServer server) {
        this.conversation = input;
        this.participants = Collections.unmodifiableList(participants);
        this.server = server;
        setDaemon(true);
        setName(String.format("citizen-memory-generator-%s", participants.stream().map(e -> e.getCitizenData().getName()).collect(Collectors.joining("_"))));
    }

    @Override
    public void run() {
        try {
            McTalking.LOGGER.debug("Starting memories generation for conversation: {}", conversation);
            String apiKey = McTalkingConfig.INSTANCE.instance().geminiApiKey;
            String memoryString;
            try {
                memoryString = GeminiFlash.sendSimpleFlashRequest(McTalkingConfig.FLASH_MODEL, apiKey, PROMPT, conversation);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                McTalking.LOGGER.debug("Memory generation thread was interrupted for conversation: {}", conversation);
                return;
            } catch (UnexpectedResponseException | IOException e) {
                McTalking.LOGGER.error("Failed to generate memories for conversation: {}", conversation, e);
                return;
            }

            GsonMemoryResponse json;
            try {
                json = GsonMemoryResponse.GSON.fromJson(memoryString, GsonMemoryResponse.class);
            } catch (JsonSyntaxException e) {
                McTalking.LOGGER.warn("Failed to parse memories response JSON: {}", memoryString);
                return;
            }

            if (shouldSaveMemory) {
                server.executeBlocking(() -> minecraftSaveMemoryRun(json));
            } else {
                savedResponse = json;
            }

            McTalking.LOGGER.info("Updated memories for conversation with {} participants", participants.size());
        } finally {
            activeGenerators.remove(this);
        }
    }

    public void scheduleOrSaveMemory() {
        if (savedResponse != null) {
            server.execute(() -> minecraftSaveMemoryRun(savedResponse));
        } else {
            shouldSaveMemory = true;
        }
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

    public static CitizenMemoryGenerator addAndGenerateMemory(String conversation, List<AbstractEntityCitizen> citizens, MinecraftServer server) {
        var generator = new CitizenMemoryGenerator(conversation, citizens, server);
        activeGenerators.add(generator);
        generator.start();

        return generator;
    }

    public static void stopAllGenerators() {
        for (var generator : activeGenerators) {
            generator.interrupt();
        }

        activeGenerators.clear();
        //TODO shutdown cleanly and wait for threads to finish
    }
}
