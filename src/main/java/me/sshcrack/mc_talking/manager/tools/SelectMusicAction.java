package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.manager.music.MusicManager;
import me.sshcrack.mc_talking.manager.music.YtDlpRunner;
import me.sshcrack.gemini_live_lib.gson.properties.ObjectProperty;
import me.sshcrack.gemini_live_lib.gson.properties.PrimitiveProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

/**
 * AI Tool: Select background music for a citizen during conversation/work.
 * 
 * Called by the Gemini Live API when the LLM decides to play background music.
 * The AI provides a direct YouTube search query, and the first matching track is played.
 * The context is cached globally so similar situations can reuse the same music selection.
 */
public class SelectMusicAction extends FunctionAction {

    public SelectMusicAction() {
        super(
            "select_music",
            "Select and play background music for the current activity and emotional context. " +
            "Provide a YouTube search query (e.g., 'relaxing medieval tavern music', 'upbeat farming work music'). " +
            "The first matching track will be played. Include a short context description (e.g., 'working as builder', 'urgent conversation'). " +
            "Only callable in citizen conversations. Music selection is cached per context to avoid frequent API calls.",
            new ObjectProperty(new HashMap<>() {{
                put("query", new PrimitiveProperty(PrimitiveProperty.Type.STRING, true));
                put("context", new PrimitiveProperty(PrimitiveProperty.Type.STRING, true));
            }})
        );
    }

    @Override
    public @NotNull JsonObject execute(
            AbstractEntityCitizen citizen,
            IColony colony,
            @Nullable JsonObject parameters) {
        
        JsonObject result = new JsonObject();

        // Check if yt-dlp is available
        if (!YtDlpRunner.isAvailable()) {
            result.addProperty("error", "yt-dlp not installed on server");
            result.addProperty("status", "disabled");
            return result;
        }

        // Extract the YouTube search query and context
        String query = null;
        String context = null;
        
        if (parameters != null) {
            if (parameters.has("query")) {
                query = parameters.get("query").getAsString();
            }
            if (parameters.has("context")) {
                context = parameters.get("context").getAsString();
            }
        }

        if (query == null || query.isBlank()) {
            result.addProperty("error", "Missing required 'query' parameter.");
            result.addProperty("status", "invalid_parameters");
            return result;
        }
        
        if (context == null || context.isBlank()) {
            result.addProperty("error", "Missing required 'context' parameter.");
            result.addProperty("status", "invalid_parameters");
            return result;
        }

        McTalking.LOGGER.debug("SelectMusicAction called for citizen {} with query: '{}', context: '{}'",
            citizen.getUUID(), query, context);

        // Get or start a music session for this citizen
        MusicManager musicManager = MusicManager.getInstance();
        boolean started = musicManager.playQueryForEntity(citizen, query, context);
        
        if (!started) {
            result.addProperty("error", "Failed to start music playback.");
            result.addProperty("status", "failed");
            return result;
        }

        // Get the track info that was just started
        var status = musicManager.getStatus(citizen.getUUID());
        if (status != null && status.has("title")) {
            result.addProperty("title", status.get("title").getAsString());
            result.addProperty("artist", status.get("artist").getAsString());
            result.addProperty("url", status.get("url").getAsString());
        }

        result.addProperty("query", query);
        result.addProperty("context", context);
        result.addProperty("status", "playing");

        return result;
    }
}
