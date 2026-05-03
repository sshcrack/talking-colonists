package me.sshcrack.mc_talking.util;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import net.minecraft.network.chat.Component;

// ⚠️ You must import your ModJobs class
import com.minecolonies.core.entity.ai.job.ModJobs;

import java.util.concurrent.ThreadLocalRandom;

public final class MumblingTopicHelper {

    private MumblingTopicHelper() {}

    private static final String FALLBACK =
            "You feel the urge to mutter something under your breath. " +
                    "Speak your thought aloud briefly, as if absent-mindedly talking to yourself.";

    public static String buildPrompt(AbstractEntityCitizen citizen) {
        if (citizen.getCitizenData() == null) return FALLBACK;

        String jobTopic = buildJobTopic(citizen);
        String statusTopic = buildStatusTopic(citizen);

        if (jobTopic == null && statusTopic == null) return FALLBACK;

        StringBuilder sb = new StringBuilder(
                "You feel the urge to mutter something under your breath. ");

        if (statusTopic != null) {
            sb.append(statusTopic).append(" ");
        } else if (jobTopic != null) {
            sb.append(jobTopic).append(" ");
        }

        sb.append("Keep it very brief — one or two sentences at most, " +
                "as if absent-mindedly talking to yourself.");

        return sb.toString();
    }

    // ── Job-specific thoughts (typed, grouped, varied) ───────────

    private static String buildJobTopic(AbstractEntityCitizen citizen) {
        var data = citizen.getCitizenData();
        if (data.getJob() == null) return null;

        var job = data.getJob().getJobRegistryEntry();

        // ── Mining / stone ─────────────────────────────
        if (job == ModJobs.miner.get() || job == ModJobs.quarrier.get()) {
            return pick(
                    "You're thinking about what you might uncover next.",
                    "You're wondering if the effort will pay off.",
                    "You're focused, a bit concerned about going deeper."
            );
        }

        if (job == ModJobs.crusher.get() || job == ModJobs.stonemason.get()) {
            return pick(
                    "You're thinking about the materials you're working with.",
                    "You're focused on getting things processed properly.",
                    "You're working steadily, feeling the strain a bit."
            );
        }

        // ── Farming / nature ───────────────────────────
        if (job == ModJobs.farmer.get() || job == ModJobs.planter.get() || job == ModJobs.florist.get()) {
            return pick(
                    "You're thinking about how everything is growing.",
                    "You're hoping the work pays off soon.",
                    "You're keeping an eye on how things are coming along."
            );
        }

        if (job == ModJobs.druid.get() || job == ModJobs.beekeeper.get()) {
            return pick(
                    "You're thinking about nature and how it's behaving.",
                    "You're paying close attention to the environment.",
                    "You're quietly observing how things are developing."
            );
        }

        // ── Animal handling ────────────────────────────
        if (job == ModJobs.shepherd.get() || job == ModJobs.cowboy.get() ||
                job == ModJobs.swineherder.get() || job == ModJobs.chickenherder.get() ||
                job == ModJobs.rabbitherder.get()) {

            return pick(
                    "You're thinking about your animals and how they're doing.",
                    "You're keeping track of everything under your care.",
                    "You're a bit concerned something might wander off."
            );
        }

        // ── Crafting / production ─────────────────────
        if (job == ModJobs.blacksmith.get() || job == ModJobs.smelter.get() ||
                job == ModJobs.glassblower.get() || job == ModJobs.dyer.get() ||
                job == ModJobs.fletcher.get() || job == ModJobs.mechanic.get() ||
                job == ModJobs.concretemixer.get()) {

            return pick(
                    "You're focused on getting the details right.",
                    "You're thinking about how everything fits together.",
                    "You're working carefully, hoping for a solid result."
            );
        }

        // ── Food ──────────────────────────────────────
        if (job == ModJobs.cook.get() || job == ModJobs.chef.get() || job == ModJobs.baker.get()) {
            return pick(
                    "You're thinking about the food you're preparing.",
                    "You're hoping everything turns out well.",
                    "You're focused on making something worthwhile."
            );
        }

        // ── Combat / guards ───────────────────────────
        if (job == ModJobs.knight.get() || job == ModJobs.ranger.get()) {
            return pick(
                    "You're alert, watching for anything unusual.",
                    "You're thinking about keeping things safe.",
                    "You're slightly on edge, just in case."
            );
        }

        // ── Delivery / logistics ──────────────────────
        if (job == ModJobs.deliveryman.get()) {
            return pick(
                    "You're thinking about where you need to go next.",
                    "You're trying to keep everything organized.",
                    "You're hoping you haven't missed anything."
            );
        }

        // ── Knowledge / magic ─────────────────────────
        if (job == ModJobs.enchanter.get() || job == ModJobs.alchemist.get() ||
                job == ModJobs.researcher.get()) {

            return pick(
                    "You're thinking about something complex you're working on.",
                    "You're trying to make sense of a difficult idea.",
                    "You're focused, but not entirely confident yet."
            );
        }

        if (job == ModJobs.teacher.get() || job == ModJobs.student.get() || job == ModJobs.pupil.get()) {
            return pick(
                    "You're thinking about what you're learning or teaching.",
                    "You're trying to stay focused on the material.",
                    "You're mulling over something you don't fully understand yet."
            );
        }

        // ── Medical / death ───────────────────────────
        if (job == ModJobs.healer.get()) {
            return pick(
                    "You're thinking about someone's condition.",
                    "You're hoping your efforts are helping.",
                    "You're focused on what needs to be done next."
            );
        }

        if (job == ModJobs.undertaker.get()) {
            return pick(
                    "You're thinking about those who have passed.",
                    "You're feeling the weight of your work.",
                    "You're quiet, focused on your responsibility."
            );
        }

        // ── Exotic ────────────────────────────────────
        if (job == ModJobs.netherworker.get()) {
            return pick(
                    "You're thinking about the dangers you've faced.",
                    "You're uneasy about where your work takes you.",
                    "You're focused, but wary."
            );
        }

        // ── Fallback ──────────────────────────────────
        String jobName = Component.translatable(job.getTranslationKey()).getString().toLowerCase();
        return "You're lost in thought about your work as a " + jobName + ".";
    }

    // ── Status logic (unchanged improvements) ─────────

    private static String buildStatusTopic(AbstractEntityCitizen citizen) {
        var data = citizen.getCitizenData();
        var status = data.getStatus();

        if (status == VisibleCitizenStatus.MOURNING)
            return "You're grieving quietly, your thoughts returning to someone you lost.";

        if (status == VisibleCitizenStatus.SICK || data.getCitizenDiseaseHandler().isSick())
            return pick(
                    "You feel awful and can't ignore it.",
                    "You're unwell and distracted.",
                    "You're trying to push through, but it's hard."
            );

        if (status == VisibleCitizenStatus.RAIDED)
            return pick(
                    "You're still shaken by what happened.",
                    "You can't fully relax yet.",
                    "You're replaying recent events in your mind."
            );

        if (status == VisibleCitizenStatus.WORKING && chance(0.4))
            return buildWorkingThought();

        if (status == VisibleCitizenStatus.BAD_WEATHER && chance(0.5))
            return "You're annoyed by the weather.";

        if (status == VisibleCitizenStatus.EAT && chance(0.6))
            return "You're focused on your meal.";

        double saturation = data.getSaturation();
        if (saturation <= 1) return "You're extremely hungry.";
        if (saturation <= 3 && chance(0.5)) return "You're thinking about food.";

        return null;
    }

    private static String buildWorkingThought() {
        return pick(
                "You're focused on your work.",
                "You're working steadily, thinking things through.",
                "You're concentrating, your mind drifting slightly."
        );
    }

    // ── Helpers ──────────────────────────────────────

    private static boolean chance(double probability) {
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    private static String pick(String... options) {
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }
}
