package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.Nullable;

public record CitizenSubState(CitizenAIState state, @Nullable MinimalAISubState type, @Nullable String context) {
}
