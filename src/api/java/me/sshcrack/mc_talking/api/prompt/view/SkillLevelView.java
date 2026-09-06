package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;

/** Stable citizen skill and current level. */
public record SkillLevelView(@NotNull CitizenSkill skill, int level) {
}
