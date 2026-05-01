package me.sshcrack.mc_talking.util;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import net.minecraft.network.chat.Component;

/**
 * Builds a job- and status-aware mumbling prompt for a citizen so they
 * mutter something relevant to what they are actually doing, rather than a
 * generic "mutter under your breath" instruction.
 */
public final class MumblingTopicHelper {

    private MumblingTopicHelper() {}

    private static final String FALLBACK =
            "You feel the urge to mutter something under your breath. " +
            "Speak your thought aloud briefly, as if absent-mindedly talking to yourself.";

    /**
     * Returns a short system instruction that tells the citizen what to mutter
     * about based on their current job and visible status.
     */
    public static String buildPrompt(AbstractEntityCitizen citizen) {
        if (citizen.getCitizenData() == null) return FALLBACK;

        String jobTopic = buildJobTopic(citizen);
        String statusTopic = buildStatusTopic(citizen);

        if (jobTopic == null && statusTopic == null) return FALLBACK;

        StringBuilder sb = new StringBuilder(
                "You feel the urge to mutter something under your breath. ");

        if (statusTopic != null) {
            sb.append(statusTopic).append(" ");
        } else {
            sb.append(jobTopic).append(" ");
        }

        sb.append("Keep it very brief — one or two sentences at most, " +
                "as if absent-mindedly talking to yourself.");

        return sb.toString();
    }

    // ── Job-specific topics ───────────────────────────────────────────────────

    private static String buildJobTopic(AbstractEntityCitizen citizen) {
        var data = citizen.getCitizenData();
        if (data.getJob() == null) return null;

        String jobKey = data.getJob().getJobRegistryEntry().getTranslationKey();
        String jobName = Component.translatable(jobKey).getString().toLowerCase();

        // Match on translation-key fragments for resilience across mod versions
        if (contains(jobKey, "miner"))      return "You're thinking about the ore vein you spotted earlier, or wondering how deep to dig next.";
        if (contains(jobKey, "farmer"))     return "You're thinking about the crops — whether it's time to harvest or if there's been enough rain.";
        if (contains(jobKey, "lumberjack")) return "You're muttering about which trees to chop next or how heavy today's load of logs was.";
        if (contains(jobKey, "builder"))    return "You're puzzling over your current construction project — materials, measurements, or a tricky wall section.";
        if (contains(jobKey, "guard") || contains(jobKey, "knight") || contains(jobKey, "ranger"))
                                            return "You're scanning your patrol area and muttering about any security concerns or suspicious things you noticed.";
        if (contains(jobKey, "cook") || contains(jobKey, "tavern"))
                                            return "You're thinking about today's meal — what ingredients you have, or what the colonists might want to eat.";
        if (contains(jobKey, "fisher"))     return "You're muttering about the fish — whether they're biting today or which spot has the best catch.";
        if (contains(jobKey, "shepherd"))   return "You're thinking about your animals — their health, or how much wool you've collected lately.";
        if (contains(jobKey, "smelter") || contains(jobKey, "blacksmith"))
                                            return "You're muttering about the furnace — whether the temperature is right or what you're forging next.";
        if (contains(jobKey, "enchanter") || contains(jobKey, "wizard") || contains(jobKey, "mage"))
                                            return "You're quietly reciting an incantation or puzzling over an enchantment formula.";
        if (contains(jobKey, "healer") || contains(jobKey, "doctor"))
                                            return "You're thinking about your patients — their symptoms, or whether you have enough medicine.";
        if (contains(jobKey, "teacher") || contains(jobKey, "school"))
                                            return "You're thinking about today's lessons or wondering if the children are paying attention.";
        if (contains(jobKey, "fletcher"))   return "You're muttering about arrow shafts — whether the feathers are aligned properly or if you need more materials.";
        if (contains(jobKey, "courier") || contains(jobKey, "deliveryman"))
                                            return "You're going over your delivery list in your head, wondering if you've forgotten anything.";
        if (contains(jobKey, "composter") || contains(jobKey, "florist"))
                                            return "You're thinking about the plants — which ones need tending or what compost mix works best.";
        if (contains(jobKey, "stone") || contains(jobKey, "quarry") || contains(jobKey, "crusher"))
                                            return "You're thinking about stone blocks — how many you've processed today and how sore your arms are.";
        if (contains(jobKey, "planter") || contains(jobKey, "forester"))
                                            return "You're thinking about where to plant the next saplings or how the forest is coming along.";
        if (contains(jobKey, "library") || contains(jobKey, "student"))
                                            return "You're quietly reciting something you read recently or mulling over a topic you're studying.";

        // Generic fallback using the translated job name
        return "You're lost in thought about your work as a " + jobName + ".";
    }

    // ── Status-specific overrides (higher priority than job) ─────────────────

    private static String buildStatusTopic(AbstractEntityCitizen citizen) {
        var data = citizen.getCitizenData();
        var status = data.getStatus();
        if (status == null) return null;

        if (status == VisibleCitizenStatus.MOURNING)   return "You're grieving quietly, whispering the name of someone you lost.";
        if (status == VisibleCitizenStatus.SICK)        return "You're feeling awful — muttering about your symptoms and hoping to feel better soon.";
        if (status == VisibleCitizenStatus.RAIDED)      return "You're still shaken from the raid, muttering nervously about staying safe.";
        if (status == VisibleCitizenStatus.BAD_WEATHER) return "You're grumbling about the dreadful weather and how it's slowing everything down.";
        if (status == VisibleCitizenStatus.EAT)         return "You're muttering to yourself about how hungry you were and how good this food tastes.";

        if (data.getCitizenDiseaseHandler().isSick()) {
            return "You feel terrible and mutter about your aches and how you need medicine.";
        }

        if (data.getHomeBuilding() == null) {
            return "You murmur quietly about not having a proper place to sleep and how uncomfortable that is.";
        }

        double saturation = data.getSaturation();
        if (saturation <= 1) return "Your stomach growls and you mutter about how desperately hungry you are.";
        if (saturation <= 3) return "You're thinking about food — muttering about what you'd love to eat right now.";

        return null; // No override; fall through to job topic
    }

    private static boolean contains(String key, String fragment) {
        return key.toLowerCase().contains(fragment);
    }
}
