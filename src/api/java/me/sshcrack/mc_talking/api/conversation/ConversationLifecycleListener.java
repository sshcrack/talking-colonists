package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;

/** Observer for conversation lifecycle. Exceptions are isolated from core and other listeners. */
@FunctionalInterface
public interface ConversationLifecycleListener {
    void onConversationLifecycle(@NotNull ConversationLifecycleEvent event);
}
