package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenSkillHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecommendJobAction extends PlayerFunctionAction {

    private static final Map<String, JobRequirement> JOB_REQUIREMENTS = new LinkedHashMap<>();

    // Weights derived from MineColonies' internal job-to-skill mappings (colony_pam.xml / schematics).
    // Update when MineColonies adds, removes, or rebalances job skill requirements.
    static {
        JOB_REQUIREMENTS.put("Builder", new JobRequirement(Skill.Strength, 1.0, Skill.Agility, 0.5));
        JOB_REQUIREMENTS.put("Miner", new JobRequirement(Skill.Strength, 1.0, Skill.Stamina, 0.5));
        JOB_REQUIREMENTS.put("Forester", new JobRequirement(Skill.Strength, 1.0, Skill.Athletics, 0.5));
        JOB_REQUIREMENTS.put("Fisherman", new JobRequirement(Skill.Agility, 1.0, Skill.Focus, 0.5));
        JOB_REQUIREMENTS.put("Farmer", new JobRequirement(Skill.Strength, 1.0, Skill.Athletics, 0.5));
        JOB_REQUIREMENTS.put("Shepherd", new JobRequirement(Skill.Focus, 1.0, Skill.Knowledge, 0.5));
        JOB_REQUIREMENTS.put("Cowhand", new JobRequirement(Skill.Focus, 1.0, Skill.Athletics, 0.5));
        JOB_REQUIREMENTS.put("Swine Herder", new JobRequirement(Skill.Focus, 1.0, Skill.Stamina, 0.5));
        JOB_REQUIREMENTS.put("Chicken Herder", new JobRequirement(Skill.Focus, 1.0, Skill.Agility, 0.5));
        JOB_REQUIREMENTS.put("Chef", new JobRequirement(Skill.Creativity, 1.0, Skill.Dexterity, 0.5));
        JOB_REQUIREMENTS.put("Baker", new JobRequirement(Skill.Creativity, 1.0, Skill.Dexterity, 0.5));
        JOB_REQUIREMENTS.put("Smelter", new JobRequirement(Skill.Strength, 1.0, Skill.Stamina, 0.5));
        JOB_REQUIREMENTS.put("Blacksmith", new JobRequirement(Skill.Strength, 1.0, Skill.Dexterity, 0.5));
        JOB_REQUIREMENTS.put("Tool Smith", new JobRequirement(Skill.Dexterity, 1.0, Skill.Intelligence, 0.5));
        JOB_REQUIREMENTS.put("Weaponsmith", new JobRequirement(Skill.Strength, 1.0, Skill.Dexterity, 0.5));
        JOB_REQUIREMENTS.put("Carpenter", new JobRequirement(Skill.Dexterity, 1.0, Skill.Creativity, 0.5));
        JOB_REQUIREMENTS.put("Deliveryman", new JobRequirement(Skill.Agility, 1.0, Skill.Athletics, 0.5));
        JOB_REQUIREMENTS.put("Warehouseman", new JobRequirement(Skill.Strength, 1.0, Skill.Focus, 0.5));
        JOB_REQUIREMENTS.put("Guard", new JobRequirement(Skill.Strength, 1.0, Skill.Agility, 0.5));
        JOB_REQUIREMENTS.put("Knight", new JobRequirement(Skill.Strength, 1.0, Skill.Stamina, 0.5));
        JOB_REQUIREMENTS.put("Archer", new JobRequirement(Skill.Agility, 1.0, Skill.Focus, 0.5));
        JOB_REQUIREMENTS.put("Alchemist", new JobRequirement(Skill.Mana, 1.0, Skill.Knowledge, 0.5));
        JOB_REQUIREMENTS.put("Enchanter", new JobRequirement(Skill.Mana, 1.0, Skill.Intelligence, 0.5));
        JOB_REQUIREMENTS.put("Student", new JobRequirement(Skill.Intelligence, 1.0, Skill.Knowledge, 0.5));
        JOB_REQUIREMENTS.put("Researcher", new JobRequirement(Skill.Intelligence, 1.0, Skill.Knowledge, 0.5));
        JOB_REQUIREMENTS.put("Teacher", new JobRequirement(Skill.Intelligence, 1.0, Skill.Focus, 0.5));
        JOB_REQUIREMENTS.put("Beekeeper", new JobRequirement(Skill.Focus, 1.0, Skill.Dexterity, 0.5));
        JOB_REQUIREMENTS.put("Composter", new JobRequirement(Skill.Strength, 1.0, Skill.Stamina, 0.5));
        JOB_REQUIREMENTS.put("Glassblower", new JobRequirement(Skill.Creativity, 1.0, Skill.Dexterity, 0.5));
        JOB_REQUIREMENTS.put("Crusher", new JobRequirement(Skill.Strength, 1.0, Skill.Stamina, 0.5));
        JOB_REQUIREMENTS.put("Sawmill", new JobRequirement(Skill.Dexterity, 1.0, Skill.Strength, 0.5));
        JOB_REQUIREMENTS.put("Plantation", new JobRequirement(Skill.Dexterity, 1.0, Skill.Athletics, 0.5));
        JOB_REQUIREMENTS.put("Dyer", new JobRequirement(Skill.Creativity, 1.0, Skill.Dexterity, 0.5));
        JOB_REQUIREMENTS.put("Fletcher", new JobRequirement(Skill.Dexterity, 1.0, Skill.Focus, 0.5));
        JOB_REQUIREMENTS.put("Mechanic", new JobRequirement(Skill.Intelligence, 1.0, Skill.Dexterity, 0.5));
        JOB_REQUIREMENTS.put("Printer", new JobRequirement(Skill.Creativity, 1.0, Skill.Dexterity, 0.5));
        JOB_REQUIREMENTS.put("Sifter", new JobRequirement(Skill.Focus, 1.0, Skill.Dexterity, 0.5));
        JOB_REQUIREMENTS.put("Stonemason", new JobRequirement(Skill.Strength, 1.0, Skill.Dexterity, 0.5));
        JOB_REQUIREMENTS.put("Healer", new JobRequirement(Skill.Knowledge, 1.0, Skill.Mana, 0.5));
    }

    public RecommendJobAction() {
        super("recommend_job", "Analyzes this citizen's skill levels and returns top job recommendations based on their aptitudes.");
    }

    @NotNull
    @Override
    public JsonObject execute(AbstractEntityCitizen citizen, IColony colony, @Nullable JsonObject parameters) {
        JsonObject obj = new JsonObject();

        var data = citizen.getCitizenData();
        if (data == null) {
            obj.addProperty("error", "Citizen data not available.");
            return obj;
        }

        var skillHandler = data.getCitizenSkillHandler();
        if (skillHandler == null) {
            obj.addProperty("error", "Skill handler not available.");
            return obj;
        }

        Map<Skill, CitizenSkillHandler.SkillData> skills = skillHandler.getSkills();
        if (skills == null || skills.isEmpty()) {
            obj.addProperty("error", "No skills available.");
            return obj;
        }

        StringBuilder skillSummary = new StringBuilder();
        skillSummary.append("Skills for ").append(data.getName()).append(":\n");
        List<Map.Entry<Skill, CitizenSkillHandler.SkillData>> sortedSkills = new ArrayList<>(skills.entrySet());
        sortedSkills.sort(Map.Entry.comparingByValue(
                Comparator.comparingInt(s -> -s.getLevel())));
        for (var entry : sortedSkills) {
            skillSummary.append("- ").append(entry.getKey().name())
                    .append(": ").append(entry.getValue().getLevel()).append("\n");
        }
        obj.addProperty("skill_summary", skillSummary.toString().trim());

        List<JobScore> scoredJobs = new ArrayList<>();
        for (var entry : JOB_REQUIREMENTS.entrySet()) {
            String jobName = entry.getKey();
            JobRequirement req = entry.getValue();

            CitizenSkillHandler.SkillData primaryData = skills.get(req.primary);
            CitizenSkillHandler.SkillData secondaryData = skills.get(req.secondary);

            int primaryLevel = primaryData != null ? primaryData.getLevel() : 1;
            int secondaryLevel = secondaryData != null ? secondaryData.getLevel() : 1;

            double score = primaryLevel * req.primaryWeight + secondaryLevel * req.secondaryWeight;

            StringBuilder reason = new StringBuilder();
            reason.append("Primary: ").append(req.primary.name()).append(" (").append(primaryLevel).append(")");
            reason.append(", Secondary: ").append(req.secondary.name()).append(" (").append(secondaryLevel).append(")");

            scoredJobs.add(new JobScore(jobName, score, reason.toString()));
        }

        scoredJobs.sort(Comparator.comparingDouble(j -> -j.score));

        JsonArray recommendations = new JsonArray();
        int topN = Math.min(5, scoredJobs.size());
        for (int i = 0; i < topN; i++) {
            JobScore js = scoredJobs.get(i);
            JsonObject j = new JsonObject();
            j.addProperty("job", js.jobName);
            j.addProperty("score", Math.round(js.score * 10.0) / 10.0);
            j.addProperty("reason", js.reason);
            recommendations.add(j);
        }

        obj.add("recommendations", recommendations);
        return obj;
    }

    private record JobRequirement(Skill primary, double primaryWeight, Skill secondary, double secondaryWeight) {}

    private record JobScore(String jobName, double score, String reason) {}
}
