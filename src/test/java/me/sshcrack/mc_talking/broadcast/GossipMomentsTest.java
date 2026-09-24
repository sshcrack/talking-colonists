package me.sshcrack.mc_talking.broadcast;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GossipMomentsTest {
    // Only the Kind enum: loading GossipMoments itself pulls in Minecraft classes, which Forge's
    // signed jars refuse in plain unit tests.
    private static String key(GossipMoments.Kind kind, String source) {
        return kind.subtitleKey(source);
    }

    @Test
    void rumorsStaySecretAndNewsNamesItsSource() throws Exception {
        assertEquals("mc_talking.gossip.rumor", key(GossipMoments.Kind.RUMOR, "the notice board"));
        assertEquals("mc_talking.gossip.news_from", key(GossipMoments.Kind.BROADCAST, "the notice board"));
        assertEquals("mc_talking.gossip.news", key(GossipMoments.Kind.BROADCAST, null));

        try (var lang = GossipMomentsTest.class.getResourceAsStream("/assets/mc_talking/lang/en_us.json")) {
            JsonObject entries = JsonParser.parseReader(new InputStreamReader(lang, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : new String[]{"mc_talking.gossip.rumor", "mc_talking.gossip.news", "mc_talking.gossip.news_from"}) {
                assertTrue(entries.has(key), key);
            }
        }
    }
}
