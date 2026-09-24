package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;

/** Receives finished utterances on the Minecraft server thread. Keep it fast. */
@FunctionalInterface
public interface ConversationUtteranceListener {
    void onUtterance(@NotNull ConversationUtteranceEvent event);
}
