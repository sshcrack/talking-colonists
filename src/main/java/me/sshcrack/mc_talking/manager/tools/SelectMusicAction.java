package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonArray;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * AI Tool: Select background music for a citizen during conversation/work.
 * 
 * Called by the Gemini Live API when the LLM decides to play background music.
 * Returns a list of YouTube music tracks that fit the current context.
 */
public class SelectMusicAction extends FunctionAction {

    public SelectMusicAction() {
        super(
            "select_music",
            "Select background music appropriate for the current activity and emotional context. " +
            "Returns a list of music tracks with metadata. Only callable in citizen conversations. " +
            "Ensures music reflects the citizen's current job, mood, and surroundings.",
            new ObjectProperty(new HashMap<>() {{
                put("query", new PrimitiveProperty(PrimitiveProperty.Type.STRING, true));
                put("context", new PrimitiveProperty(PrimitiveProperty.Type.STRING, false));
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

        // Extract the requested YouTube search query
        String query = null;
        if (parameters != null && parameters.has("query")) {
            query = parameters.get("query").getAsString();
        } else if (parameters != null && parameters.has("context")) {
            query = parameters.get("context").getAsString();
        }

        if (query == null || query.isBlank()) {
            result.addProperty("error", "Missing query parameter.");
            result.addProperty("status", "invalid_parameters");
            return result;
        }

        McTalking.LOGGER.debug("SelectMusicAction called for citizen {} with query: {}",
            citizen.getUUID(), query);

        // Get or start a music session for this citizen
        MusicManager musicManager = MusicManager.getInstance();
        var searchResult = YtDlpRunner.resolveSearchResult(query);
        if (searchResult == null) {
            result.addProperty("error", "No YouTube results found for query.");
            result.addProperty("status", "not_found");
            return result;
        }

        boolean started = musicManager.playQueryForEntity(citizen, query, "select_music");
        if (!started) {
            result.addProperty("error", "Failed to start music playback.");
            result.addProperty("status", "failed");
            return result;
        }

        JsonArray tracksArray = new JsonArray();
        JsonObject trackObj = new JsonObject();
        trackObj.addProperty("title", searchResult.title());
        trackObj.addProperty("artist", searchResult.uploader());
        trackObj.addProperty("url", searchResult.webpageUrl());
        trackObj.addProperty("age_restricted", searchResult.ageRestricted());
        tracksArray.add(trackObj);

        result.addProperty("query", query);
        result.add("tracks", tracksArray);
        result.addProperty("status", "playing");
        result.addProperty("selected_count", 1);

        return result;
    }
}
