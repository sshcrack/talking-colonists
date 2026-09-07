package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.entity.ai.JobStatus;
import me.sshcrack.mc_talking.api.prompt.view.AIWorkerState;
import me.sshcrack.mc_talking.api.prompt.view.BuilderActivityStatus;
import me.sshcrack.mc_talking.api.prompt.view.CitizenEquipmentView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenHousingStatus;
import me.sshcrack.mc_talking.api.prompt.view.CitizenRequestAvailabilityView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenVerifiedFactsView;
import me.sshcrack.mc_talking.api.prompt.view.ObservationState;
import me.sshcrack.mc_talking.api.prompt.view.ObservedValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifiedFactSemanticsTest {
    private static final long NOW = 12_345L;

    @Test
    void healthyEquippedCitizenProducesCurrentTruth() {
        var facts = new CitizenVerifiedFactsView(
                NOW,
                ObservedValue.current(100.0, NOW),
                ObservedValue.current(new CitizenEquipmentView(
                        List.of("1x Iron Helmet", "1x Iron Chestplate"),
                        List.of("1x Iron Sword")), NOW),
                CitizenHousingStatus.HOUSED,
                ObservedValue.current(new CitizenRequestAvailabilityView(List.of(), List.of()), NOW),
                BuilderActivityStatus.NOT_BUILDER
        );

        String prompt = VerifiedFactPromptRenderer.render(facts);
        assertTrue(prompt.contains("point-in-time observations override contradictory recollections"));
        assertTrue(prompt.contains("Health: 100.0% now"));
        assertTrue(prompt.contains("Iron Helmet"));
        assertTrue(prompt.contains("Iron Sword"));
        assertTrue(prompt.contains("Do not claim to be homeless"));
    }

    @Test
    void homelessAndUnknownHousingAreNotConflated() {
        String homeless = VerifiedFactPromptRenderer.render(factsWithHousing(CitizenHousingStatus.HOMELESS));
        String unknown = VerifiedFactPromptRenderer.render(factsWithHousing(CitizenHousingStatus.UNKNOWN));

        assertTrue(homeless.contains("currently no home is assigned"));
        assertTrue(unknown.contains("could not be verified"));
        assertFalse(unknown.contains("currently no home is assigned"));
    }

    @Test
    void sleepingBuilderWinsOverMaterialWait() {
        var requests = ObservedValue.current(
                new CitizenRequestAvailabilityView(List.of(), List.of("32x Oak Planks")), NOW);

        assertEquals(
                BuilderActivityStatus.SLEEPING,
                BuilderActivityClassifier.classify(true, true, JobStatus.STUCK, AIWorkerState.NEEDS_ITEM, requests));
        assertEquals(
                BuilderActivityStatus.WAITING_FOR_MATERIALS,
                BuilderActivityClassifier.classify(true, false, JobStatus.STUCK, AIWorkerState.NEEDS_ITEM, requests));
    }

    @Test
    void emptyInventoryAndUnavailableInventoryRenderDifferently() {
        var empty = new CitizenVerifiedFactsView(
                NOW,
                ObservedValue.current(100.0, NOW),
                ObservedValue.current(new CitizenEquipmentView(List.of(), List.of()), NOW),
                CitizenHousingStatus.HOUSED,
                ObservedValue.current(new CitizenRequestAvailabilityView(List.of(), List.of()), NOW),
                BuilderActivityStatus.NOT_BUILDER
        );
        var unavailable = new CitizenVerifiedFactsView(
                NOW,
                ObservedValue.current(100.0, NOW),
                ObservedValue.unavailable(ObservationState.UNAVAILABLE, NOW),
                CitizenHousingStatus.HOUSED,
                ObservedValue.current(new CitizenRequestAvailabilityView(List.of(), List.of()), NOW),
                BuilderActivityStatus.NOT_BUILDER
        );

        String emptyPrompt = VerifiedFactPromptRenderer.render(empty);
        String unavailablePrompt = VerifiedFactPromptRenderer.render(unavailable);
        assertTrue(emptyPrompt.contains("observed empty right now"));
        assertTrue(unavailablePrompt.contains("UNAVAILABLE; do not infer empty or equipped"));
        assertFalse(unavailablePrompt.contains("observed empty right now"));
    }

    @Test
    void waitingRequestDoesNotClaimWarehouseIsEmpty() {
        var facts = new CitizenVerifiedFactsView(
                NOW,
                ObservedValue.current(100.0, NOW),
                ObservedValue.current(new CitizenEquipmentView(List.of(), List.of()), NOW),
                CitizenHousingStatus.HOUSED,
                ObservedValue.current(
                        new CitizenRequestAvailabilityView(List.of(), List.of("10x Bread")), NOW),
                BuilderActivityStatus.WAITING_FOR_MATERIALS
        );
        String prompt = VerifiedFactPromptRenderer.render(facts);
        assertTrue(prompt.contains("does NOT prove colony/warehouse stock is empty"));
    }

    private static CitizenVerifiedFactsView factsWithHousing(CitizenHousingStatus housing) {
        return new CitizenVerifiedFactsView(
                NOW,
                ObservedValue.current(100.0, NOW),
                ObservedValue.current(new CitizenEquipmentView(List.of(), List.of()), NOW),
                housing,
                ObservedValue.current(new CitizenRequestAvailabilityView(List.of(), List.of()), NOW),
                BuilderActivityStatus.NOT_BUILDER
        );
    }
}
