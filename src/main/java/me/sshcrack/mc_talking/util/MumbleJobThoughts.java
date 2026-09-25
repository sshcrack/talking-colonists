package me.sshcrack.mc_talking.util;

import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import net.minecraft.network.chat.Component;

/** What a citizen mumbles about their job: one set of thoughts per MineColonies job, tinted by mood. */
final class MumbleJobThoughts {
    private MumbleJobThoughts() {
    }

    static String thought(JobEntry job) {

        // ── Mining ────────────────────────────────────────────────────────────
        if (job == ModJobs.miner.get()) {
            return MiscUtil.pick(
                    "You're thinking about what veins might lie deeper in the rock.",
                    "You're wondering whether the shaft you dug will hold.",
                    "You're mulling over a strange formation you spotted earlier.",
                    "You're focused, a bit uneasy about going further down."
            );
        }

        if (job == ModJobs.quarrier.get()) {
            return MiscUtil.pick(
                    "You're thinking about the sheer volume of stone left to move.",
                    "You're sore from the work but not about to stop.",
                    "You're planning the next section of the quarry in your head."
            );
        }

        // ── Stone / crushing ─────────────────────────────────────────────────
        if (job == ModJobs.crusher.get()) {
            return MiscUtil.pick(
                    "You're thinking about the rhythm of the work — grind, sort, repeat.",
                    "You're wondering if you're getting the ratios right.",
                    "You're working through a minor frustration with the material."
            );
        }

        if (job == ModJobs.stoneMason.get()) {
            return MiscUtil.pick(
                    "You're thinking about how to get the joints tighter.",
                    "You're picturing the finished structure in your head.",
                    "You're going over which cuts still need to be made."
            );
        }

        // ── Farming / plants ─────────────────────────────────────────────────
        if (job == ModJobs.farmer.get()) {
            return MiscUtil.pick(
                    "You're watching how the crops are coming in.",
                    "You're thinking about which fields need attention next.",
                    "You're hoping the yield holds up this season."
            );
        }

        if (job == ModJobs.planter.get()) {
            return MiscUtil.pick(
                    "You're thinking about which spots still need planting.",
                    "You're wondering if the saplings will take.",
                    "You're going over your planting plan in your head."
            );
        }

        if (job == ModJobs.florist.get()) {
            return MiscUtil.pick(
                    "You're keeping an eye on how the flowers are coming along.",
                    "You're thinking about what blooms might come next.",
                    "You're quietly enjoying the colour around you."
            );
        }

        // ── Druid / nature ───────────────────────────────────────────────────
        if (job == ModJobs.druid.get()) {
            return MiscUtil.pick(
                    "You're sensing something subtle in the environment.",
                    "You're thinking about the balance of things — something feels off.",
                    "You're paying close attention to patterns others ignore."
            );
        }

        if (job == ModJobs.beekeeper.get()) {
            return MiscUtil.pick(
                    "You're listening to the hive — something in the sound tells you things.",
                    "You're thinking about the colony and whether it's healthy.",
                    "You're noting which flowers are getting the most attention today."
            );
        }

        // ── Animal handling ──────────────────────────────────────────────────
        if (job == ModJobs.shepherd.get()) {
            return MiscUtil.pick(
                    "You're counting your flock in your head.",
                    "You're thinking about that one sheep that keeps wandering.",
                    "You're watching for anything that might startle them."
            );
        }

        if (job == ModJobs.cowboy.get()) {
            return MiscUtil.pick(
                    "You're thinking about which cattle need checking on.",
                    "You're tracking something one of the animals did earlier.",
                    "You're keeping an eye out — they can be unpredictable."
            );
        }

        if (job == ModJobs.swineHerder.get()) {
            return MiscUtil.pick(
                    "You're thinking about keeping the pigs under control.",
                    "You're a bit exasperated — they're not the easiest to manage.",
                    "You're watching to make sure none of them wander off."
            );
        }

        if (job == ModJobs.chickenHerder.get()) {
            return MiscUtil.pick(
                    "You're keeping track of the flock and whether anything's missing.",
                    "You're thinking about the noise — it's been busier than usual.",
                    "You're watching one of them that's been acting oddly."
            );
        }

        if (job == ModJobs.rabbitHerder.get()) {
            return MiscUtil.pick(
                    "You're making sure none of them have found a gap to slip through.",
                    "You're thinking about how fast they breed — it's a lot to manage.",
                    "You're watching one that keeps pushing the boundaries."
            );
        }

        // ── Smithing / smelting ──────────────────────────────────────────────
        if (job == ModJobs.blacksmith.get()) {
            return MiscUtil.pick(
                    "You're thinking through the next piece — weight, balance, heat.",
                    "You're replaying a strike pattern that didn't come out quite right.",
                    "You're mentally checking your stock of materials."
            );
        }

        if (job == ModJobs.smelter.get()) {
            return MiscUtil.pick(
                    "You're watching the temperature in your head — it has to be exact.",
                    "You're thinking about the ore that's waiting to be processed.",
                    "You're running through the timing to make sure nothing burns."
            );
        }

        // ── Crafting ─────────────────────────────────────────────────────────
        if (job == ModJobs.glassblower.get()) {
            return MiscUtil.pick(
                    "You're thinking about the shape — you need a steady hand for this.",
                    "You're going over a detail that didn't come out how you wanted.",
                    "You're picturing the finished piece before you start."
            );
        }

        if (job == ModJobs.dyer.get()) {
            return MiscUtil.pick(
                    "You're thinking about the colour balance — it needs to be right.",
                    "You're mentally mixing the next batch.",
                    "You're wondering if the hue will hold after drying."
            );
        }

        if (job == ModJobs.fletcher.get()) {
            return MiscUtil.pick(
                    "You're checking the fletching in your head — angle and weight matter.",
                    "You're thinking about how consistent your last batch was.",
                    "You're focused on the small details that make the difference."
            );
        }

        if (job == ModJobs.mechanic.get()) {
            return MiscUtil.pick(
                    "You're running through how a mechanism fits together.",
                    "You're thinking about a component that might wear out soon.",
                    "You're troubleshooting something in your head."
            );
        }

        if (job == ModJobs.concreteMixer.get()) {
            return MiscUtil.pick(
                    "You're thinking about getting the mix right this time.",
                    "You're focused on the consistency — too dry and it won't set.",
                    "You're mentally timing how long this batch needs."
            );
        }

        // ── Food ─────────────────────────────────────────────────────────────
        if (job == ModJobs.cook.get()) {
            return MiscUtil.pick(
                    "You're thinking through what you're going to make next.",
                    "You're wondering if you have all the ingredients you need.",
                    "You're running through a recipe in your head, adjusting as you go."
            );
        }

        if (job == ModJobs.chef.get()) {
            return MiscUtil.pick(
                    "You're thinking about how to get the flavour just right.",
                    "You're going over the order of things — timing is everything.",
                    "You're quietly critiquing the last thing you made."
            );
        }

        if (job == ModJobs.baker.get()) {
            return MiscUtil.pick(
                    "You're thinking about the dough and whether it's ready.",
                    "You're watching the timing in your head — overbaking is the enemy.",
                    "You're already planning what you'll bake after this."
            );
        }

        // ── Combat / guards ──────────────────────────────────────────────────
        if (job == ModJobs.knight.get()) {
            return MiscUtil.pick(
                    "You're scanning for anything out of place.",
                    "You're mentally running through your patrol route.",
                    "You're on edge — quiet stretches like this make you suspicious."
            );
        }

        if (job == ModJobs.archer.get()) {
            return MiscUtil.pick(
                    "You're thinking about your sight lines and whether they're covered.",
                    "You're watching for movement — a habit you can't shake.",
                    "You're quietly calculating range in the back of your mind."
            );
        }

        // ── Delivery / logistics ─────────────────────────────────────────────
        if (job == ModJobs.delivery.get()) {
            return MiscUtil.pick(
                    "You're mentally reordering your route for efficiency.",
                    "You're hoping you haven't left anything behind.",
                    "You're running through the list again just to be sure."
            );
        }

        // ── Knowledge / magic ────────────────────────────────────────────────
        if (job == ModJobs.enchanter.get()) {
            return MiscUtil.pick(
                    "You're turning over the logic of a formula that hasn't clicked yet.",
                    "You're thinking about the energy flows — something's still off.",
                    "You're replaying a recent attempt in your head, looking for the flaw."
            );
        }

        if (job == ModJobs.alchemist.get()) {
            return MiscUtil.pick(
                    "You're going over the sequence — order matters more than people think.",
                    "You're thinking about a reaction that didn't behave as expected.",
                    "You're mentally cataloguing what you have and what you still need."
            );
        }

        if (job == ModJobs.researcher.get()) {
            return MiscUtil.pick(
                    "You're turning a problem over in your mind from a different angle.",
                    "You're thinking about a piece of information that doesn't fit yet.",
                    "You're not sure you're asking the right question — that bothers you."
            );
        }

        if (job == ModJobs.teacher.get()) {
            return MiscUtil.pick(
                    "You're thinking about how to explain something more clearly.",
                    "You're replaying a lesson that didn't land the way you wanted.",
                    "You're figuring out a better way to approach the material."
            );
        }

        if (job == ModJobs.student.get() || job == ModJobs.pupil.get()) {
            return MiscUtil.pick(
                    "You're going over what you were just taught, trying to make it stick.",
                    "You're not sure you fully understood that last part.",
                    "You're replaying an explanation in your head."
            );
        }

        // ── Medical ──────────────────────────────────────────────────────────
        if (job == ModJobs.healer.get()) {
            return MiscUtil.pick(
                    "You're thinking about someone's condition and whether it's improving.",
                    "You're going over the treatment — wondering if you're missing something.",
                    "You're hoping your efforts are actually making a difference."
            );
        }

        if (job == ModJobs.undertaker.get()) {
            return MiscUtil.pick(
                    "You're thinking quietly about those who've passed.",
                    "You're carrying a weight that doesn't leave you easily.",
                    "You're focused on doing this right — it's the least they deserve."
            );
        }

        // ── Exotic ───────────────────────────────────────────────────────────
        if (job == ModJobs.netherworker.get()) {
            return MiscUtil.pick(
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
    static String moodTint(String base, double happiness) {
        if (happiness < 3.0) {
            return base + " " + MiscUtil.pick(
                    "A creeping frustration sits just beneath the surface.",
                    "There's a persistent dissatisfaction you can't quite shake.",
                    "Things feel harder than they should right now."
            );
        }
        if (happiness < 5.0) {
            return base + " " + MiscUtil.pick(
                    "You're not in the best mood today.",
                    "Something's been nagging at you all day.",
                    "You're a little off — nothing feels quite right."
            );
        }
        if (happiness > 8.0) {
            return base + " " + MiscUtil.pick(
                    "Despite everything, you're in good spirits.",
                    "Things feel like they're going well lately.",
                    "There's a quiet satisfaction underlying it all."
            );
        }
        return base; // neutral — no tint added
    }
}
