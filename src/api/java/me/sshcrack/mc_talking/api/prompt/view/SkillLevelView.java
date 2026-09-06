package me.sshcrack.mc_talking.api.prompt.view;

/**
 * Stable skill name and level pair for prompt generation.
 */
public record SkillLevelView(
        String name,
        int level
) {
}
