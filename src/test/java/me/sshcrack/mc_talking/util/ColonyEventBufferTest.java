package me.sshcrack.mc_talking.util;

import com.minecolonies.api.colony.IColony;
import me.sshcrack.mc_talking.api.colony.AddonColonyEvent;
import me.sshcrack.mc_talking.api.colony.ColonyEventType;
import me.sshcrack.mc_talking.api.colony.ColonyEventView;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.util.ColonyEventBuffer.ColonyEvent;
import me.sshcrack.mc_talking.util.ColonyEventBuffer.EventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColonyEventBufferTest {
    /** The listeners here ignore the colony; a proxy IColony would run MineColonies static init on Forge. */
    private static final IColony COLONY = null;

    private final List<AddonRegistration> registrations = new ArrayList<>();

    @AfterEach
    void closeRegistrations() {
        registrations.forEach(AddonRegistration::close);
    }

    private static ColonyEvent core(int i) {
        return new ColonyEvent(EventType.BUILDING_ADDED, "Building " + i, i);
    }

    private static ColonyEvent addon(String namespace, int i) {
        return new ColonyEvent(EventType.ADDON, namespace + " news " + i, i, namespace, "news");
    }

    private static Deque<ColonyEvent> newestFirst(List<ColonyEvent> events) {
        Deque<ColonyEvent> buffer = new ArrayDeque<>();
        events.forEach(buffer::addFirst);
        return buffer;
    }

    @Test
    void addonEventsCannotPushOutCoreEvents() {
        List<ColonyEvent> events = new ArrayList<>();
        for (int i = 0; i < ColonyEventBuffer.MAX_EVENTS; i++) events.add(core(i));
        for (int i = 0; i < 50; i++) events.add(addon("spammy", 100 + i));
        Deque<ColonyEvent> buffer = newestFirst(events);

        ColonyEventBuffer.trimEvents(buffer);

        assertEquals(ColonyEventBuffer.MAX_EVENTS, buffer.stream().filter(e -> !e.isAddon()).count());
        assertEquals(ColonyEventBuffer.MAX_ADDON_EVENTS_PER_NAMESPACE, buffer.stream().filter(ColonyEvent::isAddon).count());
        assertEquals("spammy news 149", buffer.peekFirst().description(), "newest addon events are kept");
    }

    @Test
    void eachNamespaceHasItsOwnBudgetAndCoreEventsStayBounded() {
        List<ColonyEvent> events = new ArrayList<>();
        for (int i = 0; i < 30; i++) events.add(core(i));
        for (int i = 0; i < 12; i++) events.add(addon("gazette", 100 + i));
        for (int i = 0; i < 3; i++) events.add(addon("elections", 200 + i));
        Deque<ColonyEvent> buffer = newestFirst(events);

        ColonyEventBuffer.trimEvents(buffer);

        assertEquals(ColonyEventBuffer.MAX_EVENTS, buffer.stream().filter(e -> !e.isAddon()).count());
        assertEquals(10, buffer.stream().filter(e -> "gazette".equals(e.addonNamespace())).count());
        assertEquals(3, buffer.stream().filter(e -> "elections".equals(e.addonNamespace())).count());
        assertTrue(buffer.stream().noneMatch(e -> e.description().equals("Building 9")), "oldest core events are dropped");
    }

    @Test
    void listenersRunInOrderAndOneFailureDoesNotStopOthers() {
        List<String> calls = new ArrayList<>();
        registrations.add(ColonyEventBuffer.registerListener("test:late", 20, (colony, event) -> calls.add("late:" + event.description())));
        registrations.add(ColonyEventBuffer.registerListener("test:broken", 10, (colony, event) -> {
            calls.add("broken");
            throw new IllegalStateException("boom");
        }));
        registrations.add(ColonyEventBuffer.registerListener("test:early", 5, (colony, event) -> calls.add("early:" + event.type())));

        ColonyEventBuffer.dispatch(COLONY, addon("elections", 1));

        assertEquals(List.of("early:ADDON", "broken", "late:elections news 1"), calls);
    }

    @Test
    void closedListenersStopReceivingEvents() {
        List<ColonyEventView> seen = new ArrayList<>();
        AddonRegistration registration = ColonyEventBuffer.registerListener("test:once", 0, (colony, event) -> seen.add(event));
        ColonyEventBuffer.dispatch(COLONY, core(1));
        registration.close();
        ColonyEventBuffer.dispatch(COLONY, core(2));

        assertEquals(1, seen.size());
        assertEquals(ColonyEventType.BUILDING_ADDED, seen.get(0).type());
        assertThrows(IllegalArgumentException.class, () -> ColonyEventBuffer.registerListener("not-namespaced", 0, (c, e) -> { }));
    }

    @Test
    void addonEventsSurviveSaveAndLoadAndStayDistinguishable() {
        ColonyEvent original = new ColonyEvent(EventType.ADDON, "Maria won the election", 42L, "elections", "election_won");
        ColonyEvent loaded = ColonyEvent.deserialize(original.serialize());

        assertEquals(original, loaded);
        ColonyEventView view = loaded.toView();
        assertTrue(view.isAddonEvent());
        assertEquals("elections", view.addonNamespace());
        assertEquals("election_won", view.addonKey());

        ColonyEvent legacyCore = ColonyEvent.deserialize(core(7).serialize());
        assertNull(legacyCore.addonNamespace());
        assertEquals(ColonyEventType.BUILDING_ADDED, legacyCore.toView().type());

        var broken = original.serialize();
        broken.remove("addonNamespace");
        assertNull(ColonyEvent.deserialize(broken), "addon events without a namespace are dropped on load");
    }

    @Test
    void everyCoreTypeHasAnApiType() {
        for (EventType type : EventType.values()) {
            assertEquals(type.name(), ColonyEventType.valueOf(type.name()).name());
        }
    }

    @Test
    void addonEventsValidateTheirFields() {
        assertThrows(IllegalArgumentException.class, () -> new AddonColonyEvent("Bad NS", "key", "Text"));
        assertThrows(IllegalArgumentException.class, () -> new AddonColonyEvent("ns", "key", " "));
        assertThrows(IllegalArgumentException.class,
                () -> new AddonColonyEvent("ns", "key", "x".repeat(AddonColonyEvent.MAX_DESCRIPTION_LENGTH + 1)));
        assertEquals("Trimmed", new AddonColonyEvent("ns", "key", " Trimmed ").description());
    }
}
