package me.sshcrack.mc_talking.conversations.memory.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CitizenMemories {
    private static final String TAG_FACTS_KEY = "facts";
    private static final String TAG_EVENTS_KEY = "events";
    private static final String TAG_RELATIONSHIPS_KEY = "relationships";
    private static final String TAG_SESSION_TOKEN = "gemini_session_token";
    private final List<String> facts = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private final List<CitizenRelationshipMemory> relationships = new ArrayList<>();
    private String sessionToken = "";

    public CitizenMemories() {
        // We do not initialize these memories because at first it is empty.
        // You can however deserialize it using @deserializeNbt if you have saved data
    }

    public List<String> getFacts() {
        return facts;
    }

    public List<String> getEvents() {
        return events;
    }

    public void addFact(String fact) {
        facts.add(fact);
    }

    public void addEvent(String event) {
        events.add(event);
    }

    /**
     * Adds a relationship change to the memories. If a change for the same citizen and relationship type already exists, it will be updated instead of adding a new entry.
     *
     * @param targetUUID the UUID of the other citizen or the affected player
     * @param type       the type of relationship change (e.g. trust, friendship, etc.)
     * @param change     the amount of change to apply to the relationship (positive or negative)
     */
    public void addRelationshipChange(@NotNull UUID targetUUID, @NotNull CitizenRelationshipChangeType type, float change) {
        for (CitizenRelationshipMemory relationship : relationships) {
            if (relationship.getTargetUUID().equals(targetUUID) && relationship.getType() == type) {
                relationship.addChange(change);
                return;
            }
        }

        relationships.add(new CitizenRelationshipMemory(targetUUID, type, change));
    }

    public CompoundTag serializeNbt() {
        CompoundTag tag = new CompoundTag();
        ListTag factsTag = new ListTag();
        for (String fact : facts) {
            factsTag.add(StringTag.valueOf(fact));
        }

        tag.put(TAG_FACTS_KEY, factsTag);
        ListTag eventsTag = new ListTag();
        for (String event : events) {
            eventsTag.add(StringTag.valueOf(event));
        }

        tag.put(TAG_EVENTS_KEY, eventsTag);

        ListTag relationshipsNbt = new ListTag();
        for (CitizenRelationshipMemory relationship : relationships) {
            relationshipsNbt.add(relationship.serializeNbt());
        }

        tag.put(TAG_RELATIONSHIPS_KEY, relationshipsNbt);
        if (sessionToken != null && !sessionToken.isBlank()) {
            tag.putString(TAG_SESSION_TOKEN, sessionToken);
        }
        return tag;
    }

    public void deserializeNbt(CompoundTag tag) {
        facts.clear();
        events.clear();
        relationships.clear();

        ListTag factsNbt = tag.getList(TAG_FACTS_KEY, Tag.TAG_STRING);
        for (int i = 0; i < factsNbt.size(); i++) {
            facts.add(factsNbt.getString(i));
        }

        ListTag eventsNbt = tag.getList(TAG_EVENTS_KEY, Tag.TAG_STRING);
        for (int i = 0; i < eventsNbt.size(); i++) {
            events.add(eventsNbt.getString(i));
        }

        ListTag relationshipsNbt = tag.getList(TAG_RELATIONSHIPS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < relationshipsNbt.size(); i++) {
            CompoundTag relationshipTag = relationshipsNbt.getCompound(i);
            CitizenRelationshipMemory relationship = new CitizenRelationshipMemory(relationshipTag);
            relationships.add(relationship);
        }
        if (tag.contains(TAG_SESSION_TOKEN)) {
            sessionToken = tag.getString(TAG_SESSION_TOKEN);
        }
    }

    public String getSessionToken() {
        return sessionToken == null ? "" : sessionToken;
    }

    public void setSessionToken(String token) {
        this.sessionToken = token == null ? "" : token;
    }

    /**
     * Formats the memories into a prompt string that can be used for LLM input. This includes facts, events, and relationship changes. The interestedParties parameter can be used to filter or prioritize certain memories based on the parties involved, but for simplicity, this implementation includes all memories.
     *
     * @param interestedParties a map with keys of the entity UUID, the value is the LLM name, that should be added from the relationships
     * @return a formatted string representing the memories, suitable for prompting an LLM
     */
    public String toPrompt(Map<UUID, String> interestedParties) {
        StringBuilder prompt = new StringBuilder();
        if (!facts.isEmpty()) {
            prompt.append(" Facts:\n");
            for (String fact : facts) {
                prompt.append("- ").append(fact).append("\n");
            }
        }

        if (!events.isEmpty()) {
            prompt.append(" Events:\n");
            for (String event : events) {
                prompt.append("- ").append(event).append("\n");
            }
        }

        List<CitizenRelationshipMemory> relevantRelationships = relationships
                .stream()
                .filter(r -> interestedParties.containsKey(r.getTargetUUID()))
                .toList();

        if (!relevantRelationships.isEmpty()) {
            prompt.append(" Relationships:\n");
            prompt.append(" These are the relationship changes that are relevant to the current conversation based on the parties involved. The factor is neutral at 0:\n");
            for (CitizenRelationshipMemory relationship : relevantRelationships) {
                String name = interestedParties.get(relationship.getTargetUUID());

                prompt.append("- Your ").append(relationship.getType()).append(" towards ")
                        .append(name).append(" is at factor ").append(relationship.getFactor()).append("\n");
            }
        }

        return prompt.toString();
    }
}
