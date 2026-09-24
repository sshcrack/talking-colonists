package me.sshcrack.mc_talking.manager.prompt;

import me.sshcrack.mc_talking.api.prompt.view.CitizenSkill;
import me.sshcrack.mc_talking.api.prompt.view.SkillLevelView;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The skills part of the citizen prompt (moved from DefaultCitizenPromptProvider). */
public final class SkillsPromptSection {
    private SkillsPromptSection() {
    }

    /** The best skill (with a flavour line when high), a second skill, and a clear weakness. */
    public static void append(List<SkillLevelView> skillLevels, StringBuilder prompt) {
        Map<CitizenSkill, Integer> skills = skillLevels.stream()
                .collect(Collectors.toMap(SkillLevelView::skill, SkillLevelView::level, Math::max));

        CitizenSkill highestSkill = null;
        int highestLevel = -1;
        CitizenSkill secondSkill = null;
        int secondLevel = -1;

        for (Map.Entry<CitizenSkill, Integer> entry : skills.entrySet()) {
            int level = entry.getValue();
            if (level > highestLevel) {
                secondSkill = highestSkill;
                secondLevel = highestLevel;
                highestSkill = entry.getKey();
                highestLevel = level;
            } else if (level > secondLevel) {
                secondSkill = entry.getKey();
                secondLevel = level;
            }
        }

        if (highestSkill != null) {
            prompt.append("\n## KEY ATTRIBUTES\n");
            prompt.append("- Best at **").append(formatSkillName(highestSkill)).append("** (level ").append(highestLevel).append(")\n");

            if (highestLevel >= 3) {
                switch (highestSkill) {
                    case INTELLIGENCE -> prompt.append("- Intellectual and thoughtful\n");
                    case STRENGTH -> prompt.append("- Values physical prowess\n");
                    case CREATIVITY -> prompt.append("- Has artistic mindset\n");
                    case KNOWLEDGE -> prompt.append("- Well-read and informative\n");
                    case DEXTERITY -> prompt.append("- Has nimble hands\n");
                    case ADAPTABILITY -> prompt.append("- Flexible and quick to adapt\n");
                    case FOCUS -> prompt.append("- Detail-oriented and methodical\n");
                    case MANA -> prompt.append("- Spiritually sensitive\n");
                    case ATHLETICS -> prompt.append("- Physically active and energetic\n");
                    case AGILITY -> prompt.append("- Quick and graceful\n");
                    case STAMINA -> prompt.append("- Has great endurance\n");
                    case UNKNOWN -> { }
                }
            }

            if (secondSkill != null && secondLevel >= 2) {
                prompt.append("- Also good at **").append(formatSkillName(secondSkill)).append("**\n");
            }

            CitizenSkill lowestSkill = null;
            int lowestLevel = Integer.MAX_VALUE;

            for (Map.Entry<CitizenSkill, Integer> entry : skills.entrySet()) {
                int level = entry.getValue();
                if (level < lowestLevel) {
                    lowestSkill = entry.getKey();
                    lowestLevel = level;
                }
            }

            if (lowestSkill != null && lowestLevel < 2 && highestLevel - lowestLevel >= 3) {
                prompt.append("- Struggles with **").append(formatSkillName(lowestSkill)).append("**\n");
            }
        }
    }

    private static String formatSkillName(CitizenSkill skill) {
        return skill.name().toLowerCase().replace('_', ' ');
    }
}
