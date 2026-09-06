package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.entity.citizen.Skill;
import me.sshcrack.mc_talking.api.prompt.view.AIWorkerState;
import me.sshcrack.mc_talking.api.prompt.view.CitizenAIState;
import me.sshcrack.mc_talking.api.prompt.view.CitizenSkill;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusType;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.MinimalAISubState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Makes MineColonies dependency drift fail Talking Colonists CI instead of becoming an addon ABI
 * break. Runtime mapping still has UNKNOWN fallbacks for unexpected patch-level values.
 */
class MineColoniesCompatibilityMapperTest {
    @Test
    void allCurrentCitizenStatesHaveStableMappings() {
        for (var upstream : com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState.values()) {
            assertNotEquals(CitizenAIState.UNKNOWN, MineColoniesCompatibilityMapper.citizenState(upstream),
                    () -> "Unmapped MineColonies citizen state: " + upstream.name());
        }
    }

    @Test
    void allCurrentWorkerStatesHaveStableMappingsAndMatchingEatPolicy() {
        for (var upstream : com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.values()) {
            AIWorkerState mapped = MineColoniesCompatibilityMapper.workerState(upstream);
            assertNotEquals(AIWorkerState.UNKNOWN, mapped,
                    () -> "Unmapped MineColonies worker state: " + upstream.name());
            assertEquals(upstream.isOkayToEat(), mapped.isOkayToEat(),
                    () -> "isOkayToEat drift for MineColonies worker state: " + upstream.name());
        }
    }

    @Test
    void allCurrentSkillsHaveStableMappings() {
        for (Skill upstream : Skill.values()) {
            assertNotEquals(CitizenSkill.UNKNOWN, MineColoniesCompatibilityMapper.skill(upstream),
                    () -> "Unmapped MineColonies skill: " + upstream.name());
        }
    }

    @Test
    void standardVisibleStatusKeysHaveStableMappingsAndUnknownFallback() {
        assertEquals(CitizenStatusType.WORKING,
                MineColoniesCompatibilityMapper.visibleStatusTranslationKey("com.minecolonies.gui.visiblestatus.working"));
        assertEquals(CitizenStatusType.SLEEP,
                MineColoniesCompatibilityMapper.visibleStatusTranslationKey("com.minecolonies.gui.visiblestatus.sleep"));
        assertEquals(CitizenStatusType.HOUSE,
                MineColoniesCompatibilityMapper.visibleStatusTranslationKey("com.minecolonies.gui.visiblestatus.idle"));
        assertEquals(CitizenStatusType.RAIDED,
                MineColoniesCompatibilityMapper.visibleStatusTranslationKey("com.minecolonies.gui.visiblestatus.raid"));
        assertEquals(CitizenStatusType.MOURNING,
                MineColoniesCompatibilityMapper.visibleStatusTranslationKey("com.minecolonies.gui.visiblestatus.mourn"));
        assertEquals(CitizenStatusType.BAD_WEATHER,
                MineColoniesCompatibilityMapper.visibleStatusTranslationKey("com.minecolonies.gui.visiblestatus.rain"));
        assertEquals(CitizenStatusType.SICK,
                MineColoniesCompatibilityMapper.visibleStatusTranslationKey("com.minecolonies.gui.visiblestatus.sick"));
        assertEquals(CitizenStatusType.EAT,
                MineColoniesCompatibilityMapper.visibleStatusTranslationKey("com.minecolonies.gui.visiblestatus.eat"));
        assertEquals(CitizenStatusType.UNKNOWN,
                MineColoniesCompatibilityMapper.visibleStatusTranslationKey("com.minecolonies.gui.visiblestatus.future_patch"));
    }

    @Test
    void allCurrentMinimalStatesHaveStableMappings() {
        for (var state : com.minecolonies.core.entity.ai.minimal.EntityAIEatTask.EatingState.values()) {
            assertNotEquals(MinimalAISubState.UNKNOWN, MineColoniesCompatibilityMapper.eatingState(state),
                    () -> "Unmapped eating state: " + state.name());
        }
        for (var state : com.minecolonies.core.entity.ai.minimal.EntityAISleep.SleepState.values()) {
            assertNotEquals(MinimalAISubState.UNKNOWN, MineColoniesCompatibilityMapper.sleepState(state),
                    () -> "Unmapped sleep state: " + state.name());
        }
        for (var state : com.minecolonies.core.entity.ai.minimal.EntityAISickTask.DiseaseState.values()) {
            assertNotEquals(MinimalAISubState.UNKNOWN, MineColoniesCompatibilityMapper.diseaseState(state),
                    () -> "Unmapped disease state: " + state.name());
        }
        for (var state : com.minecolonies.core.entity.ai.minimal.EntityAIMournCitizen.MourningState.values()) {
            assertNotEquals(MinimalAISubState.UNKNOWN, MineColoniesCompatibilityMapper.mourningState(state),
                    () -> "Unmapped mourning state: " + state.name());
        }
        for (var state : com.minecolonies.core.entity.ai.minimal.EntityAICitizenAvoidEntity.FleeStates.values()) {
            assertNotEquals(MinimalAISubState.UNKNOWN, MineColoniesCompatibilityMapper.fleeState(state),
                    () -> "Unmapped flee state: " + state.name());
        }
        for (var state : com.minecolonies.core.entity.ai.minimal.EntityAICitizenWander.WanderState.values()) {
            assertNotEquals(MinimalAISubState.UNKNOWN, MineColoniesCompatibilityMapper.wanderState(state),
                    () -> "Unmapped leisure state: " + state.name());
        }
    }

    @Test
    void happinessCompatibilityValuesAreTypedAndUnknownSafe() {
        assertEquals(HappinessModifierType.HOMELESSNESS,
                MineColoniesCompatibilityMapper.happinessModifier("homelessness"));
        assertEquals(HappinessModifierType.IDLE_AT_JOB,
                MineColoniesCompatibilityMapper.happinessModifier("idleatjob"));
        assertEquals(HappinessModifierType.RAID_WITHOUT_DEATH,
                MineColoniesCompatibilityMapper.happinessModifier("raidwithoutdeath"));
        assertEquals(HappinessModifierType.UNKNOWN,
                MineColoniesCompatibilityMapper.happinessModifier("future_patch_modifier"));
    }
}
