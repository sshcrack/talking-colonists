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
        assertEquals(Tier.DEMAND, ComplaintRamp.tier(HOMELESSNESS, 0.0, 10, OLD_COLONY, DEFAULTS));
        assertEquals(Tier.REMARK, ComplaintRamp.tier(HOMELESSNESS, 0.0, 0, OLD_COLONY, DEFAULTS));
    }

    @Test
    void problemsAreHeldAgainstThePlayerOnlyAfterFixingThemWouldHaveTakenLonger() {
        // An established colony: a job takes about a day to sort out, a home about two.
        assertEquals(Tier.REMARK, ComplaintRamp.tier(HOMELESSNESS, 0.0, 1, OLD_COLONY, DEFAULTS));
        assertEquals(Tier.COMPLAINT, ComplaintRamp.tier(HOMELESSNESS, 0.0, 2, OLD_COLONY, DEFAULTS));
        assertEquals(Tier.COMPLAINT, ComplaintRamp.tier(UNEMPLOYMENT, 0.0, 1, OLD_COLONY, DEFAULTS));
    }

    @Test
    void foundingPatienceFadesGraduallyInsteadOfFlipping() {
        // Brand new: three times the usual allowance; halfway through the grace days, twice; then normal.
        assertEquals(3.0, ComplaintRamp.patience(0, DEFAULTS), 1e-9);
        assertEquals(2.0, ComplaintRamp.patience(5, DEFAULTS), 1e-9);
        assertEquals(1.0, ComplaintRamp.patience(10, DEFAULTS), 1e-9);
        for (int age = 0; age < 12; age++) {
            double step = ComplaintRamp.patience(age, DEFAULTS) - ComplaintRamp.patience(age + 1, DEFAULTS);
            assertTrue(step >= 0 && step <= 0.21, "day " + age + " changes patience by " + step);
        }
        // The same three jobless days: a remark at founding, a complaint once the colony is established.
        assertEquals(Tier.REMARK, ComplaintRamp.tier(UNEMPLOYMENT, 0.0, 2, 0, DEFAULTS));
        assertEquals(Tier.COMPLAINT, ComplaintRamp.tier(UNEMPLOYMENT, 0.0, 3, 0, DEFAULTS));
        assertEquals(Tier.COMPLAINT, ComplaintRamp.tier(UNEMPLOYMENT, 0.0, 3, OLD_COLONY, DEFAULTS));
        assertEquals(Tier.COMPLAINT, ComplaintRamp.tier(UNEMPLOYMENT, 0.0, 14, 0, DEFAULTS));
        assertEquals(Tier.DEMAND, ComplaintRamp.tier(UNEMPLOYMENT, 0.0, 15, 0, DEFAULTS), "even a new colony runs out of patience");
        assertTrue(ComplaintRamp.isYoung(9, DEFAULTS));
        assertTrue(!ComplaintRamp.isYoung(10, DEFAULTS));
        assertEquals(1.0, ComplaintRamp.patience(0, new Settings(false, 1, 5, 10)), 1e-9, "no patience without the ramp");
        assertEquals(1.0, ComplaintRamp.patience(0, new Settings(true, 1, 5, 10, 1.0)), 1e-9, "a patience of 1 turns it off");
    }

    @Test
    void moodsAndUrgencyAreEasedTheSameWay() {
        assertEquals(0.1, ComplaintRamp.eased(0.1, OLD_COLONY, DEFAULTS), 1e-9);
        assertEquals(0.7, ComplaintRamp.eased(0.1, 0, DEFAULTS), 1e-9);
        assertEquals(1.5, ComplaintRamp.eased(1.5, 0, DEFAULTS), 1e-9, "good moods are not dampened");
        assertEquals(1.5, ComplaintRamp.unhappinessUrgency(2.0, OLD_COLONY, DEFAULTS), 1e-9);
        assertEquals(0.5, ComplaintRamp.unhappinessUrgency(2.0, 0, DEFAULTS), 1e-9);
        assertEquals(0.0, ComplaintRamp.unhappinessUrgency(6.0, 0, DEFAULTS), 1e-9);
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

    @Test
    void aBrandNewColonyIsNotHostileYet() {
        Settings settings = Settings.DEFAULTS;
        // Day 0: homeless and jobless everywhere reads as "some concerns", not "deeply unhappy".
        assertTrue(ComplaintRamp.easedHappiness(2.0, 0, settings) > 5.0);
        assertEquals(2.0, ComplaintRamp.easedHappiness(2.0, settings.youngColonyDays(), settings), 1e-9);
        assertEquals(9.0, ComplaintRamp.easedHappiness(9.0, 0, settings), 1e-9);
    }
}
