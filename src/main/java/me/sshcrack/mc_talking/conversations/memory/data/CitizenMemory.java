package me.sshcrack.mc_talking.conversations.memory.data;

import com.minecolonies.core.colony.CitizenData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public class CitizenMemory {
    private static final String TAG_FACTS_KEY = "facts";
    private static final String TAG_EVENTS_KEY = "events";
    private static final String TAG_RELATIONSHIPS_KEY = "relationships";
    private final List<String> facts = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private final List<CitizenRelationshipMemory> relationships = new ArrayList<>();

    public CitizenMemory() {
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

    public void addRelationshipChange(CitizenData citizen, CitizenRelationshipChangeType type, float change) {
        for (CitizenRelationshipMemory relationship : relationships) {
            if (relationship.getOtherCitizenId().equals(citizen.getUUID()) && relationship.getType() == type) {
                relationship.addChange(change);
                return;
            }
        }

        relationships.add(new CitizenRelationshipMemory(citizen.getUUID(), type, change));
    }

    public CompoundTag serializeNbt(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ListTag facts = new ListTag();
        for (String fact : this.facts) {
            facts.add(StringTag.valueOf(fact));
        }

        tag.put(TAG_FACTS_KEY, facts);
        ListTag events = new ListTag();
        for (String event : this.events) {
            events.add(StringTag.valueOf(event));
        }

        tag.put(TAG_EVENTS_KEY, events);

        ListTag relationshipsNbt = new ListTag();
        for (CitizenRelationshipMemory relationship : relationships) {
            relationshipsNbt.add(relationship.serializeNbt());
        }

        tag.put(TAG_RELATIONSHIPS_KEY, relationshipsNbt);
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
    }
}
