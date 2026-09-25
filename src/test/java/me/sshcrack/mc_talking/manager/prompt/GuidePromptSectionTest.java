package me.sshcrack.mc_talking.manager.prompt;

import me.sshcrack.mc_talking.api.guide.AddonGuide;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidePromptSectionTest {
    private static final AddonGuide BOARD = new AddonGuide("tc_noticeboard:board", "Notice Board",
            "Post notices citizens read and reply to.", List.of("Craft a Notice Board.", "Right-click it and post."),
            List.of("Operators: /noticeboard rush"));

    @Test
    void citizensKnowEachGuideButNotTheOperatorNotes() {
        String section = GuidePromptSection.render(List.of(BOARD));
        assertTrue(section.contains("## HOW THINGS WORK IN THE COLONY"));
        assertTrue(section.contains("- Notice Board: Post notices citizens read and reply to. To start: Craft a Notice Board. Right-click it and post.\n"));
        assertTrue(!section.contains("/noticeboard rush"));
        assertEquals("", GuidePromptSection.render(List.of()));
    }

    @Test
    void guidesAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new AddonGuide("no-namespace", "T", "S", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AddonGuide("a:b", " ", "S", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AddonGuide("a:b", "x".repeat(AddonGuide.MAX_TITLE + 1), "S", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AddonGuide("a:b", "T", "S", List.of("1", "2", "3", "4", "5", "6", "7")));
        assertEquals("Title", new AddonGuide("a:b", "  Title ", "S", List.of()).title());
    }
}
