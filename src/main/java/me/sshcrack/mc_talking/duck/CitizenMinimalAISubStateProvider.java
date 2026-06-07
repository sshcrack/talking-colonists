package me.sshcrack.mc_talking.duck;

import me.sshcrack.mc_talking.api.prompt.view.MinimalAISubState;
import org.jetbrains.annotations.Nullable;

public interface CitizenMinimalAISubStateProvider {
    void mc_talking$setMinimalAiSubState(@Nullable MinimalAISubState state);
    @Nullable MinimalAISubState mc_talking$getMinimalAiSubState();
}
