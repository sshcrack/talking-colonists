package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.api.conversation.ConversationLifecycleEvent;
import me.sshcrack.mc_talking.api.conversation.ConversationLifecycleListener;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

/** Runtime-only ordered, failure-isolated lifecycle listener registry. */
public final class ConversationEventRuntime {
    private static final System.Logger LOGGER = System.getLogger("mc_talking-api");
    private static final RegistrationRegistry<ConversationLifecycleListener> LISTENERS =
            new RegistrationRegistry<>("Conversation lifecycle listener");

    private ConversationEventRuntime() {
    }

    public static @NotNull AddonRegistration register(
            @NotNull String id,
            int order,
            @NotNull ConversationLifecycleListener listener
    ) {
        return LISTENERS.register(id, order, listener);
    }

    public static void emit(@NotNull ConversationLifecycleEvent event) {
        for (var registration : LISTENERS.orderedSnapshot()) {
            try {
                registration.value().onConversationLifecycle(event);
            } catch (Throwable t) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "Conversation lifecycle listener " + registration.id() + " failed and was skipped", t);
            }
        }
    }
}
