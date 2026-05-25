package me.sshcrack.mc_talking.conversations.memory.data;

import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CitizenMemories {
    private static final String TAG_FACTS_KEY = "facts";
    private static final String TAG_EVENTS_KEY = "events";
    private static final String TAG_RELATIONSHIPS_KEY = "relationships";
    private static final String TAG_SESSION_TOKEN = "gemini_session_token";
    private static final String TAG_COMPLAINT_COUNTERS = "complaint_counters";
    private static final String TAG_LAST_COMPLAINT_TIMES = "last_complaint_times";
    private static final String TAG_LAST_COMPLAINT_DECAY_DAY = "last_complaint_decay_day";
    private final List<String> facts = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private final List<CitizenRelationshipMemory> relationships = new ArrayList<>();
    private final Map<HappinessModifierType, Integer> complaintCounters = new EnumMap<>(HappinessModifierType.class);
    private final Map<HappinessModifierType, Long> lastComplaintGameTimes = new EnumMap<>(HappinessModifierType.class);
    private String sessionToken = "";
    private int lastComplaintDecayDay = -1;

    public CitizenMemories() {
        // We do not initialize this memories because at first it is empty.
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

        if (!complaintCounters.isEmpty()) {
            CompoundTag complaintTag = new CompoundTag();
            for (Map.Entry<HappinessModifierType, Integer> entry : complaintCounters.entrySet()) {
                complaintTag.putInt(entry.getKey().name(), entry.getValue());
            }
            tag.put(TAG_COMPLAINT_COUNTERS, complaintTag);
        }

        if (!lastComplaintGameTimes.isEmpty()) {
            CompoundTag lastTimesTag = new CompoundTag();
            for (Map.Entry<HappinessModifierType, Long> entry : lastComplaintGameTimes.entrySet()) {
                lastTimesTag.putLong(entry.getKey().name(), entry.getValue());
            }
            tag.put(TAG_LAST_COMPLAINT_TIMES, lastTimesTag);
        }

        if (lastComplaintDecayDay >= 0) {
            tag.putInt(TAG_LAST_COMPLAINT_DECAY_DAY, lastComplaintDecayDay);
        }
        return tag;
    }

    public void deserializeNbt(CompoundTag tag) {
        facts.clear();
        events.clear();
        relationships.clear();
        complaintCounters.clear();
        lastComplaintGameTimes.clear();
        lastComplaintDecayDay = -1;

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

        if (tag.contains(TAG_COMPLAINT_COUNTERS, Tag.TAG_COMPOUND)) {
            CompoundTag complaintTag = tag.getCompound(TAG_COMPLAINT_COUNTERS);
            for (String key : complaintTag.getAllKeys()) {
                HappinessModifierType type = parseModifierType(key);
                if (type == null) {
                    continue;
                }
                complaintCounters.put(type, complaintTag.getInt(key));
            }
        }

        if (tag.contains(TAG_LAST_COMPLAINT_TIMES, Tag.TAG_COMPOUND)) {
            CompoundTag lastTimesTag = tag.getCompound(TAG_LAST_COMPLAINT_TIMES);
            for (String key : lastTimesTag.getAllKeys()) {
                HappinessModifierType type = parseModifierType(key);
                if (type == null) {
                    continue;
                }
                lastComplaintGameTimes.put(type, lastTimesTag.getLong(key));
            }
        }

        if (tag.contains(TAG_LAST_COMPLAINT_DECAY_DAY, Tag.TAG_INT)) {
            lastComplaintDecayDay = tag.getInt(TAG_LAST_COMPLAINT_DECAY_DAY);
        }
    }

    public String getSessionToken() {
        return sessionToken == null ? "" : sessionToken;
    }

    public void setSessionToken(String token) {
        this.sessionToken = token == null ? "" : token;
    }

    public void decayComplaintCountersForDay(int currentDay, int decayPerDay) {
        if (currentDay < 0) {
            return;
        }

        if (lastComplaintDecayDay < 0) {
            lastComplaintDecayDay = currentDay;
            return;
        }

        int dayDiff = currentDay - lastComplaintDecayDay;
        if (dayDiff <= 0) {
            return;
        }

        int totalDecay = Math.max(0, decayPerDay) * dayDiff;
        if (totalDecay > 0) {
            Iterator<Map.Entry<HappinessModifierType, Integer>> iterator = complaintCounters.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<HappinessModifierType, Integer> entry = iterator.next();
                int decayed = entry.getValue() - totalDecay;
                if (decayed <= 0) {
                    iterator.remove();
                    lastComplaintGameTimes.remove(entry.getKey());
                } else {
                    entry.setValue(decayed);
                }
            }
        }

        lastComplaintDecayDay = currentDay;
    }

    public boolean shouldSuppressComplaint(HappinessModifierType type, long currentGameTime, int repeatThreshold, long cooldownTicks) {
        if (type == null || repeatThreshold <= 0 || cooldownTicks <= 0) {
            return false;
        }

        int count = complaintCounters.getOrDefault(type, 0);
        if (count < repeatThreshold) {
            return false;
        }

        long lastComplaintTime = lastComplaintGameTimes.getOrDefault(type, Long.MIN_VALUE);
        if (lastComplaintTime == Long.MIN_VALUE || currentGameTime < lastComplaintTime) {
            return false;
        }

        return (currentGameTime - lastComplaintTime) <= cooldownTicks;
    }

    public void recordComplaintMention(HappinessModifierType type, long currentGameTime, long cooldownTicks) {
        if (type == null) {
            return;
        }

        long lastComplaintTime = lastComplaintGameTimes.getOrDefault(type, Long.MIN_VALUE);
        int previous = complaintCounters.getOrDefault(type, 0);

        int next;
        if (lastComplaintTime == Long.MIN_VALUE || cooldownTicks <= 0 || currentGameTime < lastComplaintTime || (currentGameTime - lastComplaintTime) > cooldownTicks) {
            next = 1;
        } else {
            next = previous + 1;
        }

        complaintCounters.put(type, next);
        lastComplaintGameTimes.put(type, currentGameTime);
    }

    private static HappinessModifierType parseModifierType(String rawType) {
        try {
            return HappinessModifierType.valueOf(rawType);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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
