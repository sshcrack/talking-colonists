package me.sshcrack.mc_talking.conversations.memory.data;

import me.sshcrack.mc_talking.broadcast.ColonyBroadcast;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CitizenMemories {
    private static final String TAG_FACTS_KEY = "facts";
    private static final String TAG_EVENTS_KEY = "events";
    private static final String TAG_RELATIONSHIPS_KEY = "relationships";
    private static final String TAG_SESSION_TOKEN = "gemini_session_token";
    private static final String TAG_SUMMARIZED_MEMORY = "summarized_memory";
    private static final String TAG_BROADCASTS = "mc_talking_broadcasts";
    private static final String TAG_PENDING_RUMORS = "pending_rumors";
    private final List<String> facts = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private final List<CitizenRelationshipMemory> relationships = new ArrayList<>();
    private final List<ColonyBroadcast> receivedBroadcasts = new ArrayList<>();
    private final Set<String> knownBroadcastIds = new HashSet<>();
    private final List<String> pendingRumorPropagations = new ArrayList<>();
    private String sessionToken = "";
    private String summarizedMemory = "";

    public CitizenMemories() {
    }

    public List<String> getFacts() {
        return facts;
    }

    public List<String> getEvents() {
        return events;
    }

    public List<CitizenRelationshipMemory> getRelationships() {
        return relationships;
    }

    public void addFact(String fact) {
        facts.add(fact);
    }

    public void addEvent(String event) {
        events.add(event);
        if (event.startsWith("Rumor:")) {
            pendingRumorPropagations.add(event);
        }
    }

    public boolean hasPendingRumor() {
        return !pendingRumorPropagations.isEmpty();
    }

    public String drainPendingRumor() {
        return pendingRumorPropagations.isEmpty() ? null : pendingRumorPropagations.remove(0);
    }

    public int getPendingRumorCount() {
        return pendingRumorPropagations.size();
    }

    public String peekPendingRumor(int index) {
        if (index < 0 || index >= pendingRumorPropagations.size()) return null;
        return pendingRumorPropagations.get(index);
    }

    public void setSummarizedMemory(String summarizedMemory) {
        this.summarizedMemory = summarizedMemory == null ? "" : summarizedMemory;
    }

    public String getSummarizedMemory() {
        return summarizedMemory;
    }

    public void addRelationshipChange(@NotNull UUID targetUUID, @NotNull CitizenRelationshipChangeType type, float change) {
        for (CitizenRelationshipMemory relationship : relationships) {
            if (relationship.getTargetUUID().equals(targetUUID) && relationship.getType() == type) {
                relationship.addChange(change);
                return;
            }
        }

        relationships.add(new CitizenRelationshipMemory(targetUUID, type, change));
    }

    public void addBroadcast(ColonyBroadcast broadcast) {
        if (knownBroadcastIds.contains(broadcast.getId())) return;
        knownBroadcastIds.add(broadcast.getId());
        receivedBroadcasts.add(broadcast);
        receivedBroadcasts.sort(Comparator.comparingLong(ColonyBroadcast::getCreatedAtMs).reversed());
        int max = McTalkingConfig.INSTANCE.instance().maxBroadcastsStored;
        while (receivedBroadcasts.size() > max) {
            ColonyBroadcast removed = receivedBroadcasts.remove(receivedBroadcasts.size() - 1);
            knownBroadcastIds.remove(removed.getId());
        }
    }

    public boolean hasHeardBroadcast(String id) {
        return knownBroadcastIds.contains(id);
    }

    public List<ColonyBroadcast> getReceivedBroadcasts() {
        return Collections.unmodifiableList(receivedBroadcasts);
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

        ListTag broadcastsNbt = new ListTag();
        for (ColonyBroadcast broadcast : receivedBroadcasts) {
            broadcastsNbt.add(broadcast.serialize());
        }
        tag.put(TAG_BROADCASTS, broadcastsNbt);

        ListTag pendingNbt = new ListTag();
        for (String r : pendingRumorPropagations) {
            pendingNbt.add(StringTag.valueOf(r));
        }
        tag.put(TAG_PENDING_RUMORS, pendingNbt);

        if (sessionToken != null && !sessionToken.isBlank()) {
            tag.putString(TAG_SESSION_TOKEN, sessionToken);
        }
        if (!summarizedMemory.isBlank()) {
            tag.putString(TAG_SUMMARIZED_MEMORY, summarizedMemory);
        }
        return tag;
    }

    public void deserializeNbt(CompoundTag tag) {
        facts.clear();
        events.clear();
        relationships.clear();
        receivedBroadcasts.clear();
        knownBroadcastIds.clear();
        pendingRumorPropagations.clear();

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

        ListTag broadcastsNbt = tag.getList(TAG_BROADCASTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < broadcastsNbt.size(); i++) {
            ColonyBroadcast broadcast = ColonyBroadcast.deserialize(broadcastsNbt.getCompound(i));
            receivedBroadcasts.add(broadcast);
            knownBroadcastIds.add(broadcast.getId());
        }

        ListTag pendingNbt = tag.getList(TAG_PENDING_RUMORS, Tag.TAG_STRING);
        for (int i = 0; i < pendingNbt.size(); i++) {
            pendingRumorPropagations.add(pendingNbt.getString(i));
        }

        if (tag.contains(TAG_SESSION_TOKEN)) {
            sessionToken = tag.getString(TAG_SESSION_TOKEN);
        }
        if (tag.contains(TAG_SUMMARIZED_MEMORY)) {
            summarizedMemory = tag.getString(TAG_SUMMARIZED_MEMORY);
        }
    }

    public String getSessionToken() {
        return sessionToken == null ? "" : sessionToken;
    }

    public void setSessionToken(String token) {
        this.sessionToken = token == null ? "" : token;
    }

    public String toPrompt(Map<UUID, String> interestedParties) {
        StringBuilder prompt = new StringBuilder();

        if (!summarizedMemory.isBlank()) {
            prompt.append(" Summarized Memory:\n");
            prompt.append(" ").append(summarizedMemory).append("\n\n");
        }

        if (!events.isEmpty()) {
            prompt.append(" Recent Events:\n");
            for (String event : events) {
                prompt.append("- ").append(event).append("\n");
            }
        }

        if (!facts.isEmpty()) {
            prompt.append(" Recent Facts:\n");
            for (String fact : facts) {
                prompt.append("- ").append(fact).append("\n");
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

        int broadcastCap = McTalkingConfig.INSTANCE.instance().maxBroadcastsInPrompt;
        if (!receivedBroadcasts.isEmpty() && broadcastCap > 0) {
            prompt.append(" Colony Broadcasts (most recent first):\n");
            int count = 0;
            for (ColonyBroadcast b : receivedBroadcasts) {
                if (count >= broadcastCap) break;
                prompt.append("- ").append(b.getOriginatorName())
                    .append(" sent word: ").append(b.getMessage()).append("\n");
                count++;
            }
        }

        return prompt.toString();
    }
}
