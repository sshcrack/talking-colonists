package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;

/**
 * Observer for conversation lifecycle. Callbacks are delivered on the Minecraft server thread and
 * exceptions are isolated from core and other listeners. Implementations should return promptly.
 */
@FunctionalInterface
public interface ConversationLifecycleListener {
    void onConversationLifecycle(@NotNull ConversationLifecycleEvent event);
}
