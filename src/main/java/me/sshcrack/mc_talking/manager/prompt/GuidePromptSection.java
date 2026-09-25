package me.sshcrack.mc_talking.manager.prompt;

import me.sshcrack.mc_talking.api.guide.AddonGuide;

import java.util.List;

/**
 * What citizens know about how the colony's features work (the Colony Handbook's chapters), so they can
 * explain them in character when a player asks.
 */
public final class GuidePromptSection {
    private GuidePromptSection() {
    }

    public static String render(List<AddonGuide> guides) {
        if (guides.isEmpty()) return "";
        StringBuilder prompt = new StringBuilder("\n## HOW THINGS WORK IN THE COLONY\n");
        prompt.append("If the player asks how something works or what they can do, explain it briefly in your own words, "
                + "as someone who lives here would. Do not recite this list, and only bring it up when it helps. "
                + "The Colony Handbook (a book and a paper) explains all of it.\n");
        for (AddonGuide guide : guides) {
            prompt.append("- ").append(guide.title()).append(": ").append(guide.summary());
            if (!guide.steps().isEmpty()) prompt.append(" To start: ").append(String.join(" ", guide.steps()));
            prompt.append("\n");
        }
        return prompt.toString();
    }
}
