package me.sshcrack.mc_talking.manager.music;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Structured context information for music selection.
 * Used to cache context-to-query mappings and provide consistent music selection.
 */
public record MusicContext(
    String activity,      // e.g., "working", "mumbling", "player conversation", "urgent contact"
    @Nullable String job, // e.g., "Builder", "Farmer", "Guard"
    @Nullable String citizenName,
    @Nullable String mood  // e.g., "happy", "stressed", "tired"
) {
    /**
     * Create a MusicContext from a citizen and activity description.
     */
    public static MusicContext fromCitizen(AbstractEntityCitizen citizen, String activity) {
        String job = null;
        if (citizen.getCitizenData() != null && citizen.getCitizenData().getJob() != null) {
            job = citizen.getCitizenData().getJob().getJobRegistryEntry().getTranslationKey();
        }
        
        String citizenName = citizen.getName().getString();
        
        // TODO: Could extract mood from citizen data (happiness, saturation, etc.)
        String mood = null;
        
        return new MusicContext(activity, job, citizenName, mood);
    }
    
    /**
     * Create a short description of this context for AI prompts.
     */
    public String toShortDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(activity);
        
        if (job != null) {
            sb.append(", job: ").append(job);
        }
        
        if (mood != null) {
            sb.append(", mood: ").append(mood);
        }
        
        return sb.toString();
    }
    
    /**
     * Create a cache key from this context.
     * Ignores citizenName to allow reuse across citizens with similar contexts.
     */
    public String toCacheKey() {
        return String.format("%s|%s|%s", 
            activity != null ? activity : "",
            job != null ? job : "",
            mood != null ? mood : ""
        );
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MusicContext that = (MusicContext) o;
        return Objects.equals(activity, that.activity) &&
               Objects.equals(job, that.job) &&
               Objects.equals(mood, that.mood);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(activity, job, mood);
    }
}
