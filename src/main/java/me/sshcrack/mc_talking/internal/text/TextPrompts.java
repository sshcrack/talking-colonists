package me.sshcrack.mc_talking.internal.text;

import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.ColonyPromptView;
import me.sshcrack.mc_talking.internal.prompt.PromptRuntime;

/** System prompts for addon text generation (roadmap A3). */
public final class TextPrompts {
    private TextPrompts() {
    }

    /** The citizen's normal detailed context (with prompt contributors) plus writing rules. */
    public static String citizen(CitizenPromptView view) {
        String language = view.conversation().responseLanguageName();
        return PromptRuntime.getDetailedCitizenInfoPrompt(view) + """

                ## WRITING TASK
                You are %1$s. Write only the text you are asked for, in character, as %1$s would write it.
                Let your mood, job and memories colour the wording, but do not invent facts about the colony.
                Write in %2$s, even though these instructions are in English.
                Plain text only: no markdown, no stage directions, no quotation marks around the whole text.
                """.formatted(view.identity().name(), language);
    }

    /** The colony's shared context, for text in the colony's collective voice. */
    public static String colony(ColonyPromptView colony, String language) {
        StringBuilder prompt = new StringBuilder("# COLONY ").append(colony.name()).append("\n\n");
        if (colony.foundingPlayer() != null) prompt.append("- Founded by ").append(colony.foundingPlayer()).append(".\n");
        prompt.append("- The colony is ").append(colony.ageDays()).append(" days old.\n");
        if (colony.environment() != null) prompt.append("- ").append(colony.environment()).append("\n");
        if (colony.milestone() != null) prompt.append("- ").append(colony.milestone()).append("\n");
        if (colony.lastRaidEndTimeTicks() != null) {
            prompt.append("- The colony survived a raid");
            if (colony.lastRaidLostCitizens() > 0) {
                prompt.append(" in which ").append(colony.lastRaidLostCitizens()).append(" colonists died");
            }
            prompt.append(".\n");
        }
        if (!colony.recentEvents().isEmpty()) {
            prompt.append("\n## RECENT COLONY EVENTS\n");
            colony.recentEvents().forEach(event -> prompt.append("- ").append(event).append("\n"));
        }
        if (!colony.connections().isEmpty()) {
            prompt.append("\n## NEIGHBOURING COLONIES\n");
            colony.connections().forEach(connection -> prompt.append("- ").append(connection).append("\n"));
        }
        prompt.append("""

                ## WRITING TASK
                You write on behalf of the colony %1$s as a whole, like its town crier, notice board or newspaper,
                not as any single colonist. Use only the facts above and in the request; do not invent names or events.
                Write in %2$s, even though these instructions are in English.
                Plain text only: no markdown, no stage directions.
                """.formatted(colony.name(), language));
        return prompt.toString();
    }
}
