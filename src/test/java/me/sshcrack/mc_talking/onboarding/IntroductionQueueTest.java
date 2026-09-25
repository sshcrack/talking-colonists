package me.sshcrack.mc_talking.onboarding;

import me.sshcrack.mc_talking.api.intro.Introduction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntroductionQueueTest {
    private static final Introduction WELCOME = intro("mc_talking:welcome");
    private static final Introduction CAMPFIRE = intro("tc_campfire:first_dusk");
    private static final Introduction LETTERS = intro("tc_postal:letters");
    private static final List<Introduction> ALL = List.of(WELCOME, CAMPFIRE, LETTERS);

    private static Introduction intro(String id) {
        return new Introduction(id, "a topic", "Tell them about it.", null, (player, colony) -> true);
    }

    @Test
    void theWelcomeComesFirst() {
        assertEquals(Optional.of(WELCOME), IntroductionQueue.next(ALL, Set.of(), introduction -> true));
    }

    @Test
    void eachIntroductionIsHeardOnce() {
        assertEquals(Optional.of(CAMPFIRE), IntroductionQueue.next(ALL, Set.of(WELCOME.id()), introduction -> true));
        assertEquals(Optional.empty(), IntroductionQueue.next(ALL, Set.of(WELCOME.id(), CAMPFIRE.id(), LETTERS.id()),
                introduction -> true));
    }

    @Test
    void introductionsWaitUntilTheyAreDue() {
        assertEquals(Optional.of(LETTERS), IntroductionQueue.next(ALL, Set.of(WELCOME.id()),
                introduction -> introduction != CAMPFIRE));
        assertEquals(Optional.empty(), IntroductionQueue.next(ALL, Set.of(WELCOME.id()), introduction -> false));
    }

    @Test
    void introductionsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new Introduction("no-namespace", "t", "h", null, (p, c) -> true));
        assertThrows(IllegalArgumentException.class, () -> new Introduction("a:b", "x".repeat(Introduction.MAX_TOPIC + 1), "h", null, (p, c) -> true));
        assertThrows(IllegalArgumentException.class, () -> new Introduction("a:b", "t", "x".repeat(Introduction.MAX_HINT + 1), null, (p, c) -> true));
        assertThrows(IllegalArgumentException.class, () -> new Introduction("a:b", "t", "h", "bad guide", (p, c) -> true));
        assertThrows(NullPointerException.class, () -> new Introduction("a:b", "t", "h", null, null));
    }
}
