package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;

/** Minimal building information intentionally detached from MineColonies implementation classes. */
public record BuildingView(@NotNull String displayName, int level) {
}
