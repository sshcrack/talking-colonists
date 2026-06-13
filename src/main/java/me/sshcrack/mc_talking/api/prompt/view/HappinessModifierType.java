package me.sshcrack.mc_talking.api.prompt.view;

import com.minecolonies.api.util.constant.HappinessConstants;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * MineColonies-independent duplicate of happiness modifier identifiers.
 */
@SuppressWarnings("SpellCheckingInspection")
public enum HappinessModifierType {
    HOMELESSNESS,
    UNEMPLOYMENT,
    HEALTH,
    IDLEATJOB,
    SCHOOL,
    MYSTICAL_SITE,
    SECURITY,
    SOCIAL,
    DAMAGE,
    DEATH,
    RAIDWITHOUTDEATH,
    FOOD,
    SLEPTTONIGHT,
    UNKNOWN;

    private static final Map<String, HappinessModifierType> BY_MC_ID = Map.ofEntries(
        Map.entry(HappinessConstants.HOMELESSNESS, HOMELESSNESS),
        Map.entry(HappinessConstants.UNEMPLOYMENT, UNEMPLOYMENT),
        Map.entry(HappinessConstants.HEALTH, HEALTH),
        Map.entry(HappinessConstants.IDLEATJOB, IDLEATJOB),
        Map.entry(HappinessConstants.SCHOOL, SCHOOL),
        Map.entry(HappinessConstants.MYSTICAL_SITE, MYSTICAL_SITE),
        Map.entry(HappinessConstants.SECURITY, SECURITY),
        Map.entry(HappinessConstants.SOCIAL, SOCIAL),
        Map.entry(HappinessConstants.DAMAGE, DAMAGE),
        Map.entry(HappinessConstants.DEATH, DEATH),
        Map.entry(HappinessConstants.RAIDWITHOUTDEATH, RAIDWITHOUTDEATH),
        Map.entry(HappinessConstants.FOOD, FOOD),
        Map.entry(HappinessConstants.SLEPTTONIGHT, SLEPTTONIGHT)
    );

    public static @Nullable HappinessModifierType fromId(String mcModifierId) {
        return BY_MC_ID.get(mcModifierId);
    }
}
