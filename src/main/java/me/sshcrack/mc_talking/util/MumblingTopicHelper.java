package me.sshcrack.mc_talking.util;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.entity.ai.JobStatus;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class MumblingTopicHelper {

    private MumblingTopicHelper() {
    }

    private static final String FALLBACK =
            "You feel the urge to mutter something under your breath. " +
                    "Speak your thought aloud briefly, as if absent-mindedly talking to yourself.";

    public static String buildPrompt(AbstractEntityCitizen citizen) {
        if (citizen.getCitizenData() == null) return FALLBACK;

        var data = citizen.getCitizenData();
        double happiness = data.getCitizenHappinessHandler().getHappiness(data.getColony(), data);

        String statusTopic = buildStatusTopic(citizen);
        boolean critical = isCriticalStatus(citizen);

        // Critical statuses (grief, illness, raid) take full priority — no blending.
        if (critical && statusTopic != null) {
            return compose(statusTopic);
        }

        String jobTopic = buildJobTopic(citizen, happiness);

        // Colony milestone — rolls independently so it can supplement any topic.
        String rawMilestone = null;
        if (chance(0.35)) {
            rawMilestone = ColonyStatsHelper.getColonyMilestoneText(data);
        }

        // Non-critical status + job: blend them so both colour the thought.
        if (statusTopic != null && jobTopic != null) {
            String blended = statusTopic + " Still, " +
                    Character.toLowerCase(jobTopic.charAt(0)) + jobTopic.substring(1);
            if (rawMilestone != null) {
                blended = blended + " You also find yourself thinking: " + rawMilestone.toLowerCase();
            }
            return compose(blended);
        }

        if (statusTopic != null) {
            if (rawMilestone != null) {
                return compose(statusTopic + " You also notice: " + rawMilestone.toLowerCase());
            }
            return compose(statusTopic);
        }

        if (jobTopic != null) {
            if (rawMilestone != null) {
                return compose(jobTopic + " You're also aware that " + rawMilestone.toLowerCase());
            }
            return compose(jobTopic);
        }

        if (rawMilestone != null) {
            return compose(standaloneColonyTopic(rawMilestone));
        }

        return FALLBACK;
    }

    private static String compose(String topic) {
        return "You feel the urge to mutter something under your breath. " +
                topic + " " +
                "Keep it very brief — one or two sentences at most, as if absent-mindedly talking to yourself.";
    }

    /**
     * Returns true for statuses that are emotionally dominant and should not be blended.
     */
    private static boolean isCriticalStatus(AbstractEntityCitizen citizen) {
        var data = citizen.getCitizenData();
        var status = data.getStatus();
        return status == VisibleCitizenStatus.MOURNING
                || status == VisibleCitizenStatus.SICK
                || data.getCitizenDiseaseHandler().isSick()
                || (status == VisibleCitizenStatus.RAIDED && !isPeaceful(citizen))
                || data.getSaturation() <= 1;
    }

    // ── Job-specific thoughts ─────────────────────────────────────────────────

    private static String buildJobTopic(AbstractEntityCitizen citizen, double happiness) {
        var data = citizen.getCitizenData();
        if (data.getJob() == null) return null;

        var job = data.getJob().getJobRegistryEntry();
        String base = buildJobThought(job);
        if (base == null) return null;

        return applyMoodTint(base, happiness);
    }

    private static String buildJobThought(JobEntry job) {

        // ── Mining ────────────────────────────────────────────────────────────
        if (job == ModJobs.miner.get()) {
            return pick(
                    "You're thinking about what veins might lie deeper in the rock.",
                    "You're wondering whether the shaft you dug will hold.",
                    "You're mulling over a strange formation you spotted earlier.",
                    "You're focused, a bit uneasy about going further down."
            );
        }

        if (job == ModJobs.quarrier.get()) {
            return pick(
                    "You're thinking about the sheer volume of stone left to move.",
                    "You're sore from the work but not about to stop.",
                    "You're planning the next section of the quarry in your head."
            );
        }

        // ── Stone / crushing ─────────────────────────────────────────────────
        if (job == ModJobs.crusher.get()) {
            return pick(
                    "You're thinking about the rhythm of the work — grind, sort, repeat.",
                    "You're wondering if you're getting the ratios right.",
                    "You're working through a minor frustration with the material."
            );
        }

        if (job == ModJobs.stoneMason.get()) {
            return pick(
                    "You're thinking about how to get the joints tighter.",
                    "You're picturing the finished structure in your head.",
                    "You're going over which cuts still need to be made."
            );
        }

        // ── Farming / plants ─────────────────────────────────────────────────
        if (job == ModJobs.farmer.get()) {
            return pick(
                    "You're watching how the crops are coming in.",
                    "You're thinking about which fields need attention next.",
                    "You're hoping the yield holds up this season."
            );
        }

        if (job == ModJobs.planter.get()) {
            return pick(
                    "You're thinking about which spots still need planting.",
                    "You're wondering if the saplings will take.",
                    "You're going over your planting plan in your head."
            );
        }

        if (job == ModJobs.florist.get()) {
            return pick(
                    "You're keeping an eye on how the flowers are coming along.",
                    "You're thinking about what blooms might come next.",
                    "You're quietly enjoying the colour around you."
            );
        }

        // ── Druid / nature ───────────────────────────────────────────────────
        if (job == ModJobs.druid.get()) {
            return pick(
                    "You're sensing something subtle in the environment.",
                    "You're thinking about the balance of things — something feels off.",
                    "You're paying close attention to patterns others ignore."
            );
        }

        if (job == ModJobs.beekeeper.get()) {
            return pick(
                    "You're listening to the hive — something in the sound tells you things.",
                    "You're thinking about the colony and whether it's healthy.",
                    "You're noting which flowers are getting the most attention today."
            );
        }

        // ── Animal handling ──────────────────────────────────────────────────
        if (job == ModJobs.shepherd.get()) {
            return pick(
                    "You're counting your flock in your head.",
                    "You're thinking about that one sheep that keeps wandering.",
                    "You're watching for anything that might startle them."
            );
        }

        if (job == ModJobs.cowboy.get()) {
            return pick(
                    "You're thinking about which cattle need checking on.",
                    "You're tracking something one of the animals did earlier.",
                    "You're keeping an eye out — they can be unpredictable."
            );
        }

        if (job == ModJobs.swineHerder.get()) {
            return pick(
                    "You're thinking about keeping the pigs under control.",
                    "You're a bit exasperated — they're not the easiest to manage.",
                    "You're watching to make sure none of them wander off."
            );
        }

        if (job == ModJobs.chickenHerder.get()) {
            return pick(
                    "You're keeping track of the flock and whether anything's missing.",
                    "You're thinking about the noise — it's been busier than usual.",
                    "You're watching one of them that's been acting oddly."
            );
        }

        if (job == ModJobs.rabbitHerder.get()) {
            return pick(
                    "You're making sure none of them have found a gap to slip through.",
                    "You're thinking about how fast they breed — it's a lot to manage.",
                    "You're watching one that keeps pushing the boundaries."
            );
        }

        // ── Smithing / smelting ──────────────────────────────────────────────
        if (job == ModJobs.blacksmith.get()) {
            return pick(
                    "You're thinking through the next piece — weight, balance, heat.",
                    "You're replaying a strike pattern that didn't come out quite right.",
                    "You're mentally checking your stock of materials."
            );
        }

        if (job == ModJobs.smelter.get()) {
            return pick(
                    "You're watching the temperature in your head — it has to be exact.",
                    "You're thinking about the ore that's waiting to be processed.",
                    "You're running through the timing to make sure nothing burns."
            );
        }

        // ── Crafting ─────────────────────────────────────────────────────────
        if (job == ModJobs.glassblower.get()) {
            return pick(
                    "You're thinking about the shape — you need a steady hand for this.",
                    "You're going over a detail that didn't come out how you wanted.",
                    "You're picturing the finished piece before you start."
            );
        }

        if (job == ModJobs.dyer.get()) {
            return pick(
                    "You're thinking about the colour balance — it needs to be right.",
                    "You're mentally mixing the next batch.",
                    "You're wondering if the hue will hold after drying."
            );
        }

        if (job == ModJobs.fletcher.get()) {
            return pick(
                    "You're checking the fletching in your head — angle and weight matter.",
                    "You're thinking about how consistent your last batch was.",
                    "You're focused on the small details that make the difference."
            );
        }

        if (job == ModJobs.mechanic.get()) {
            return pick(
                    "You're running through how a mechanism fits together.",
                    "You're thinking about a component that might wear out soon.",
                    "You're troubleshooting something in your head."
            );
        }

        if (job == ModJobs.concreteMixer.get()) {
            return pick(
                    "You're thinking about getting the mix right this time.",
                    "You're focused on the consistency — too dry and it won't set.",
                    "You're mentally timing how long this batch needs."
            );
        }

        // ── Food ─────────────────────────────────────────────────────────────
        if (job == ModJobs.cook.get()) {
            return pick(
                    "You're thinking through what you're going to make next.",
                    "You're wondering if you have all the ingredients you need.",
                    "You're running through a recipe in your head, adjusting as you go."
            );
        }

        if (job == ModJobs.chef.get()) {
            return pick(
                    "You're thinking about how to get the flavour just right.",
                    "You're going over the order of things — timing is everything.",
                    "You're quietly critiquing the last thing you made."
            );
        }

        if (job == ModJobs.baker.get()) {
            return pick(
                    "You're thinking about the dough and whether it's ready.",
                    "You're watching the timing in your head — overbaking is the enemy.",
                    "You're already planning what you'll bake after this."
            );
        }

        // ── Combat / guards ──────────────────────────────────────────────────
        if (job == ModJobs.knight.get()) {
            return pick(
                    "You're scanning for anything out of place.",
                    "You're mentally running through your patrol route.",
                    "You're on edge — quiet stretches like this make you suspicious."
            );
        }

        if (job == ModJobs.archer.get()) {
            return pick(
                    "You're thinking about your sight lines and whether they're covered.",
                    "You're watching for movement — a habit you can't shake.",
                    "You're quietly calculating range in the back of your mind."
            );
        }

        // ── Delivery / logistics ─────────────────────────────────────────────
        if (job == ModJobs.delivery.get()) {
            return pick(
                    "You're mentally reordering your route for efficiency.",
                    "You're hoping you haven't left anything behind.",
                    "You're running through the list again just to be sure."
            );
        }

        // ── Knowledge / magic ────────────────────────────────────────────────
        if (job == ModJobs.enchanter.get()) {
            return pick(
                    "You're turning over the logic of a formula that hasn't clicked yet.",
                    "You're thinking about the energy flows — something's still off.",
                    "You're replaying a recent attempt in your head, looking for the flaw."
            );
        }

        if (job == ModJobs.alchemist.get()) {
            return pick(
                    "You're going over the sequence — order matters more than people think.",
                    "You're thinking about a reaction that didn't behave as expected.",
                    "You're mentally cataloguing what you have and what you still need."
            );
        }

        if (job == ModJobs.researcher.get()) {
            return pick(
                    "You're turning a problem over in your mind from a different angle.",
                    "You're thinking about a piece of information that doesn't fit yet.",
                    "You're not sure you're asking the right question — that bothers you."
            );
        }

        if (job == ModJobs.teacher.get()) {
            return pick(
                    "You're thinking about how to explain something more clearly.",
                    "You're replaying a lesson that didn't land the way you wanted.",
                    "You're figuring out a better way to approach the material."
            );
        }

        if (job == ModJobs.student.get() || job == ModJobs.pupil.get()) {
            return pick(
                    "You're going over what you were just taught, trying to make it stick.",
                    "You're not sure you fully understood that last part.",
                    "You're replaying an explanation in your head."
            );
        }

        // ── Medical ──────────────────────────────────────────────────────────
        if (job == ModJobs.healer.get()) {
            return pick(
                    "You're thinking about someone's condition and whether it's improving.",
                    "You're going over the treatment — wondering if you're missing something.",
                    "You're hoping your efforts are actually making a difference."
            );
        }

        if (job == ModJobs.undertaker.get()) {
            return pick(
                    "You're thinking quietly about those who've passed.",
                    "You're carrying a weight that doesn't leave you easily.",
                    "You're focused on doing this right — it's the least they deserve."
            );
        }

        // ── Exotic ───────────────────────────────────────────────────────────
        if (job == ModJobs.netherworker.get()) {
            return pick(
                    "You're thinking about what's waiting for you down there.",
                    "You're still processing something you saw on your last trip.",
                    "You're uneasy — the Nether has a way of getting under your skin."
            );
        }

        // ── Fallback ─────────────────────────────────────────────────────────
        String jobName = Component.translatable(job.getTranslationKey()).getString().toLowerCase();
        return "You're lost in thought about your work as a " + jobName + ".";
    }

    /**
     * Appends a mood-colored sentence to a neutral job thought based on the
     * citizen's current happiness level. Neutral happiness (5–8) is left untouched
     * to avoid every mumble feeling melodramatic.
     */
    private static String applyMoodTint(String base, double happiness) {
        if (happiness < 3.0) {
            return base + " " + pick(
                    "A creeping frustration sits just beneath the surface.",
                    "There's a persistent dissatisfaction you can't quite shake.",
                    "Things feel harder than they should right now."
            );
        }
        if (happiness < 5.0) {
            return base + " " + pick(
                    "You're not in the best mood today.",
                    "Something's been nagging at you all day.",
                    "You're a little off — nothing feels quite right."
            );
        }
        if (happiness > 8.0) {
            return base + " " + pick(
                    "Despite everything, you're in good spirits.",
                    "Things feel like they're going well lately.",
                    "There's a quiet satisfaction underlying it all."
            );
        }
        return base; // neutral — no tint added
    }

    // ── Status logic ─────────────────────────────────────────────────────────

    private static String buildStatusTopic(AbstractEntityCitizen citizen) {
        var data = citizen.getCitizenData();
        var status = data.getStatus();

        // ── Critical ─────────────────────────────────────────────────────────
        if (status == VisibleCitizenStatus.MOURNING)
            return "You're grieving quietly, your thoughts returning to someone you lost.";

        if (status == VisibleCitizenStatus.SICK || data.getCitizenDiseaseHandler().isSick())
            return pick(
                    "You feel awful and can't ignore it.",
                    "You're unwell and finding it hard to concentrate.",
                    "You're trying to push through, but it's hard."
            );

        if (status == VisibleCitizenStatus.RAIDED && !isPeaceful(citizen))
            return pick(
                    "You're still shaken by what happened.",
                    "You can't fully relax yet.",
                    "You're replaying recent events in your mind."
            );

        double saturation = data.getSaturation();
        if (saturation <= 1)
            return pick(
                    "You're extremely hungry — it's getting hard to think straight.",
                    "The hunger is impossible to ignore now.",
                    "You're running on empty and you know it."
            );

        // ── Mild (blendable) ─────────────────────────────────────────────────
        // WORKING is intentionally omitted — job topic is always more specific.

        if (status == VisibleCitizenStatus.BAD_WEATHER && chance(0.5))
            return pick(
                    "You're annoyed by the weather.",
                    "The rain is getting to you.",
                    "You'd rather be doing this in better conditions."
            );

        if (status == VisibleCitizenStatus.EAT && chance(0.6))
            return pick(
                    "You're focused on your meal.",
                    "You're savouring a moment of rest.",
                    "The food is a welcome break."
            );

        if (saturation <= 3 && chance(0.5))
            return pick(
                    "You're thinking about food.",
                    "Your stomach reminds you it's not been long enough since you last ate.",
                    "You're keeping an eye out for a chance to grab something."
            );

        return null;
    }

    /**
     * Wraps a raw colony milestone in an intro phrase for standalone use
     * (no other topic available to blend with).
     */
    private static String standaloneColonyTopic(String milestone) {
        return pick(
                "You're thinking about something — " + milestone.toLowerCase(),
                "A thought crosses your mind: " + milestone.toLowerCase(),
                "You can't help but notice that " + milestone.toLowerCase()
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean chance(double probability) {
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    private static boolean isPeaceful(AbstractEntityCitizen citizen) {
        var level = citizen.level();
        return level != null && level.getDifficulty() == Difficulty.PEACEFUL;
    }

    private static String pick(String... options) {
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }


    public static String buildUrgentContactPrompt(AbstractEntityCitizen citizen, String playerName) {
        if (citizen.getCitizenData() == null) return buildGenericUrgentPrompt(playerName);

        var data = citizen.getCitizenData();

        // ── CRITICAL: blocking task (STUCK) ──────────────
        if (data.getJobStatus() == JobStatus.STUCK) {
            String jobName = data.getJob() != null
                    ? Component.translatable(data.getJob().getJobRegistryEntry().getTranslationKey()).getString()
                    : "worker";
            String needed = collectNeededItems(data);
            if (!needed.isEmpty()) {
                return format(playerName, pick(
                        "You're completely stuck and can't do your work as a " + jobName + ". You need: " + needed + ". Call out to %s by name and urgently explain you're blocked and need help.",
                        "You can't continue your job as a " + jobName + " because you're missing something important: " + needed + ". Call out to %s by name and ask for assistance.",
                        "As a " + jobName + ", you're stuck waiting for supplies: " + needed + ". Call out to %s by name and plead for help to get back to work."
                ));
            } else {
                return format(playerName, pick(
                        "You're completely stuck and can't do your work as a " + jobName + ". Call out to %s by name and urgently explain what's wrong and ask for help.",
                        "You can't continue your job as a " + jobName + " because you're blocked by missing supplies. Call out to %s by name and ask for assistance."
                ));
            }
        }

        // ── CRITICAL: sickness ───────────────────────────
        if (data.getCitizenDiseaseHandler().isSick()) {
            return format(playerName, pick(
                    "You are sick and feel terrible. Call out to %s by name and urgently ask for medicine or help.",
                    "You feel awful and can't ignore it anymore. Call out to %s by name and beg for help.",
                    "You're getting worse. Call out to %s by name and urgently ask for treatment."
            ));
        }

        // ── CRITICAL: starvation ─────────────────────────
        double saturation = data.getSaturation();
        if (saturation <= 1) {
            return format(playerName, pick(
                    "You are starving and weak. Call out to %s by name and beg for food.",
                    "You can barely keep going from hunger. Call out to %s by name for something to eat.",
                    "You're desperate for food. Call out to %s by name and plead for help."
            ));
        }

        // ── CRITICAL: no home ────────────────────────────
        if (data.getHomeBuilding() == null && !CitizenHelper.isCitizenGuard(citizen)) {
            return format(playerName, pick(
                    "You have nowhere to sleep. Call out to %s by name and urgently ask for help.",
                    "You're distressed about having no home. Call out to %s by name and plead for shelter.",
                    "You can't keep going like this without a place to rest. Call out to %s by name."
            ));
        }

        // ── LOW HAPPINESS ────────────────────────────────
        double happiness = data.getCitizenHappinessHandler().getHappiness(data.getColony(), data);
        if (happiness < 3.0) {
            return format(playerName, pick(
                    "You're miserable and can't hold it in anymore. Call out to %s by name and voice your frustration.",
                    "You've had enough. Call out to %s by name and demand something change.",
                    "You're deeply unhappy. Call out to %s by name and explain what's wrong."
            ));
        }

        // ── LOW HEALTH ───────────────────────────────────
        var entity = data.getEntity();
        if (entity.isPresent()) {
            double healthPercent = (entity.get().getHealth() / Math.max(1.0, entity.get().getMaxHealth())) * 100.0;

            if (healthPercent < 25.0) {
                return format(playerName, pick(
                        "You're badly injured and in pain. Call out to %s by name and ask for help.",
                        "You're struggling to stay on your feet. Call out to %s by name urgently.",
                        "You're hurt and need help. Call out to %s by name right now."
                ));
            }
        }

        return buildGenericUrgentPrompt(playerName);
    }

    /**
     * Queries the citizen's work building for open requests and returns a comma-separated
     * list of the items/equipment they are waiting for. Returns empty string if nothing found.
     * Limited to the first 5 items to keep the prompt bounded.
     */
    private static String collectNeededItems(ICitizenData data) {
        IBuilding workBuilding = data.getWorkBuilding();
        if (workBuilding == null) return "";

        Collection<IRequest<?>> openRequests = workBuilding.getOpenRequests(data.getId());
        if (openRequests == null || openRequests.isEmpty()) return "";

        List<String> items = new ArrayList<>();
        for (IRequest<?> req : openRequests) {
            if (items.size() >= 5) {
                items.add("...and more");
                break;
            }
            String display = req.getShortDisplayString().getString();
            if (display != null && !display.isEmpty()) {
                items.add(display);
            }
        }

        if (items.isEmpty()) return "";
        return String.join(", ", items);
    }

    private static String format(String playerName, String template) {
        return String.format(
                template + " Keep it brief — two or three sentences.",
                playerName
        );
    }

    private static String buildGenericUrgentPrompt(String playerName) {
        return String.format(
                "You notice %s nearby and feel the need to speak. Call out to them by name and say what's on your mind. Keep it brief — two or three sentences.",
                playerName
        );
    }
}
