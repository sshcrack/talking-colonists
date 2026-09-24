package me.sshcrack.mc_talking.api.service;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.text.TextRequest;
import me.sshcrack.mc_talking.api.text.TextResult;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/** Runtime service for addon text generation (roadmap A3). */
public interface TextService {
    @NotNull CompletableFuture<TextResult> generate(@NotNull AbstractEntityCitizen citizen, @NotNull TextRequest request);

    @NotNull CompletableFuture<TextResult> generateColonyVoice(@NotNull IColony colony, @NotNull TextRequest request);
}
