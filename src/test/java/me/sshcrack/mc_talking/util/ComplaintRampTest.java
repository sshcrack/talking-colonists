package me.sshcrack.mc_talking.util;

import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierView;
import me.sshcrack.mc_talking.util.ComplaintRamp.Settings;
import me.sshcrack.mc_talking.util.ComplaintRamp.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType.HOMELESSNESS;
import static me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType.UNEMPLOYMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplaintRampTest {
    private static final Settings DEFAULTS = Settings.DEFAULTS;
    private static final int OLD_COLONY = 30;

    @Test
    void newHomelessCitizenInNewColonyStartsAtTheMildestTier() {
        assertEquals(Tier.REMARK, ComplaintRamp.tier(HOMELESSNESS, 0.0, 0, 0, DEFAULTS));
    }

    @Test
    void tiersEscalateWithTheDaysMineColoniesCounts() {
        assertEquals(Tier.REMARK, ComplaintRamp.tier(UNEMPLOYMENT, 0.0, 0, OLD_COLONY, DEFAULTS));
        assertEquals(Tier.COMPLAINT, ComplaintRamp.tier(UNEMPLOYMENT, 0.0, 1, OLD_COLONY, DEFAULTS));
        assertEquals(Tier.COMPLAINT, ComplaintRamp.tier(UNEMPLOYMENT, 0.0, 4, OLD_COLONY, DEFAULTS));
        assertEquals(Tier.DEMAND, ComplaintRamp.tier(UNEMPLOYMENT, 0.0, 5, OLD_COLONY, DEFAULTS));
    }

    @Test
    void resolvingAProblemResetsItsTier() {
        // MineColonies resets the day count to 0 when the factor recovers; the next occurrence starts mild.
        assertEquals(Tier.DEMAND, ComplaintRamp.tier(HOMELESSNESS, 0.0, 9, OLD_COLONY, DEFAULTS));
        assertEquals(Tier.REMARK, ComplaintRamp.tier(HOMELESSNESS, 0.0, 0, OLD_COLONY, DEFAULTS));
    }

    @Test
    void everyProblemStaysARemarkWhileTheColonyIsYoung() {
        // Nothing in a new colony has been neglected yet, it just has not been built.
        assertEquals(Tier.REMARK, ComplaintRamp.tier(HOMELESSNESS, 0.0, 6, 6, DEFAULTS));
        assertEquals(Tier.REMARK, ComplaintRamp.tier(UNEMPLOYMENT, 0.0, 6, 6, DEFAULTS));
        assertEquals(Tier.DEMAND, ComplaintRamp.tier(HOMELESSNESS, 0.0, 6, 7, DEFAULTS));
        assertEquals(Tier.DEMAND, ComplaintRamp.tier(UNEMPLOYMENT, 0.0, 6, 7, DEFAULTS));
        assertTrue(ComplaintRamp.isYoung(6, DEFAULTS));
        assertTrue(!ComplaintRamp.isYoung(6, new Settings(false, 1, 5, 7)), "no grace without the ramp");
    }

    @Test
    void generalUnhappinessBarelyCountsAsUrgentInAYoungColony() {
        assertEquals(1.5, ComplaintRamp.unhappinessUrgency(2.0, OLD_COLONY, DEFAULTS), 1e-9);
        assertEquals(0.45, ComplaintRamp.unhappinessUrgency(2.0, 1, DEFAULTS), 1e-9);
        assertEquals(0.0, ComplaintRamp.unhappinessUrgency(6.0, 1, DEFAULTS), 1e-9);
    }

    @Test
    void thresholdsAreConfigurableAndKeptInOrder() {
        Settings slow = new Settings(true, 3, 10, 0);
        assertEquals(Tier.REMARK, ComplaintRamp.tier(UNEMPLOYMENT, 0.0, 2, OLD_COLONY, slow));
        assertEquals(Tier.COMPLAINT, ComplaintRamp.tier(UNEMPLOYMENT, 0.0, 9, OLD_COLONY, slow));
        Settings inverted = new Settings(true, 4, 2, 0);
        assertEquals(4, inverted.demandAfterDays(), "a demand never comes before a complaint");
    }

    @Test
    void disabledRampFollowsOnlySeverityLikeBefore() {
        Settings off = new Settings(false, 1, 5, 7);
        assertEquals(Tier.DEMAND, ComplaintRamp.tier(HOMELESSNESS, 0.1, 0, 0, off));
        assertEquals(Tier.COMPLAINT, ComplaintRamp.tier(HOMELESSNESS, 0.5, 0, 0, off));
    }

    @Test
    void activeTierIgnoresModifiersThatAreNotAProblem() {
        List<HappinessModifierView> modifiers = List.of(
                new HappinessModifierView(HOMELESSNESS, 1.0, 0),
                new HappinessModifierView(UNEMPLOYMENT, 0.4, 2));
        assertNull(ComplaintRamp.activeTier(modifiers, HOMELESSNESS, OLD_COLONY, DEFAULTS));
        assertEquals(Tier.COMPLAINT, ComplaintRamp.activeTier(modifiers, UNEMPLOYMENT, OLD_COLONY, DEFAULTS));
        assertNull(ComplaintRamp.activeTier(modifiers, HappinessModifierType.HEALTH, OLD_COLONY, DEFAULTS));
    }

    @Test
    void homelessUrgencyGrowsWithTheTier() {
        assertTrue(ComplaintRamp.homelessUrgency(Tier.REMARK) < ComplaintRamp.homelessUrgency(Tier.COMPLAINT));
        assertTrue(ComplaintRamp.homelessUrgency(Tier.COMPLAINT) < ComplaintRamp.homelessUrgency(Tier.DEMAND));
    }
}
