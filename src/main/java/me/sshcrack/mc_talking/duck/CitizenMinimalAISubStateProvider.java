package me.sshcrack.mc_talking.duck;

import me.sshcrack.mc_talking.api.prompt.view.MinimalAISubState;
import org.jetbrains.annotations.Nullable;

public interface CitizenMinimalAISubStateProvider {
    void mc_talking$setMinimalAiSubState(@Nullable MinimalAISubState state);

    default void mc_talking$setMinimalAiSubState(@Nullable MinimalAISubState state, @Nullable String context) {
        mc_talking$setMinimalAiSubState(state);
        mc_talking$setMinimalAiSubStateContext(context);
    }

    @Nullable MinimalAISubState mc_talking$getMinimalAiSubState();
    void mc_talking$setMinimalAiSubStateContext(@Nullable String context);
    @Nullable String mc_talking$getMinimalAiSubStateContext();
}
