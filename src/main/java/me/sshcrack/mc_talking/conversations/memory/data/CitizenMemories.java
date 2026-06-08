package me.sshcrack.mc_talking.conversations.memory.data;

import me.sshcrack.mc_talking.broadcast.ColonyBroadcast;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.rumor.Rumor;
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
    public static final String SYSTEM_EVENT_PREFIX = "[SYSTEM]";

    private static final String TAG_FACTS_KEY = "facts";
    private static final String TAG_EVENTS_KEY = "events";
    private static final String TAG_RELATIONSHIPS_KEY = "relationships";
    private static final String TAG_SESSION_TOKEN = "gemini_session_token";
    private static final String TAG_SUMMARIZED_MEMORY = "summarized_memory";
    private static final String TAG_BROADCASTS = "mc_talking_broadcasts";
    private static final String TAG_RUMORS = "mc_talking_rumors";
    private final List<String> facts = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private final List<CitizenRelationshipMemory> relationships = new ArrayList<>();
    private final List<ColonyBroadcast> receivedBroadcasts = new ArrayList<>();
    private final Set<String> knownBroadcastIds = new HashSet<>();
    private final List<Rumor> receivedRumors = new ArrayList<>();
    private final Set<String> knownRumorIds = new HashSet<>();
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
    }

    public void removeEventsIf(java.util.function.Predicate<String> predicate) {
        events.removeIf(predicate);
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

    public void addRumor(Rumor rumor) {
        if (knownRumorIds.contains(rumor.getId())) return;
        knownRumorIds.add(rumor.getId());
        receivedRumors.add(0, rumor);
        int max = McTalkingConfig.INSTANCE.instance().maxRumorsStored;
        while (receivedRumors.size() > max) {
            Rumor removed = receivedRumors.remove(receivedRumors.size() - 1);
            knownRumorIds.remove(removed.getId());
        }
    }

    public boolean hasHeardRumor(String id) {
        return knownRumorIds.contains(id);
    }

    public List<Rumor> getReceivedRumors() {
        return Collections.unmodifiableList(receivedRumors);
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

        ListTag rumorsNbt = new ListTag();
        for (Rumor r : receivedRumors) {
            rumorsNbt.add(r.serialize());
        }
        tag.put(TAG_RUMORS, rumorsNbt);

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
        receivedRumors.clear();
        knownRumorIds.clear();

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

        ListTag rumorsNbt = tag.getList(TAG_RUMORS, Tag.TAG_COMPOUND);
        for (int i = 0; i < rumorsNbt.size(); i++) {
            Rumor rumor = Rumor.deserialize(rumorsNbt.getCompound(i));
            receivedRumors.add(rumor);
            knownRumorIds.add(rumor.getId());
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
                prompt.append("- ").append(b.getSenderPlayerName())
                    .append(" sent word via ").append(b.getOriginatorName())
                    .append(": ").append(b.getMessage()).append("\n");
                count++;
            }
        }

        int rumorCap = McTalkingConfig.INSTANCE.instance().maxRumorsInPrompt;
        if (!receivedRumors.isEmpty() && rumorCap > 0) {
            prompt.append(" Rumors (heard via the grapevine, most recent first):\n");
            int count = 0;
            for (Rumor r : receivedRumors) {
                if (count >= rumorCap) break;
                prompt.append("- You heard (originally from ")
                    .append(r.getOriginatorName())
                    .append("): ")
                    .append(r.getContent())
                    .append("\n");
                count++;
            }
        }

        return prompt.toString();
    }
}
