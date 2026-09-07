package me.sshcrack.mc_talking.api;

import me.sshcrack.mc_talking.api.service.ContextService;
import me.sshcrack.mc_talking.api.service.ConversationRuleService;
import me.sshcrack.mc_talking.api.service.ConversationService;
import me.sshcrack.mc_talking.api.service.MemoryService;
import me.sshcrack.mc_talking.api.service.PregenerationService;
import me.sshcrack.mc_talking.api.service.PromptService;
import me.sshcrack.mc_talking.api.service.ToolService;
import org.jetbrains.annotations.NotNull;

/**
 * Root entry point for the Talking Colonists addon API.
 *
 * <p>The normal Talking Colonists mod contains the runtime implementation. The separate
 * {@code mc_talking-api} artifact is only a compile/source surface for addon developers and must
 * not be installed as an additional mod.</p>
 */
public final class TalkingColonistsApi {
    /** Breaking API generation for addon compatibility declarations. */
    public static final int API_MAJOR_VERSION = 2;

    private static final String IMPLEMENTATION_CLASS =
            "me.sshcrack.mc_talking.internal.api.TalkingColonistsApiBackend";
    private static volatile Services services;

    private TalkingColonistsApi() {
    }

    /** Returns whether the normal Talking Colonists runtime is present and exposes this API. */
    public static boolean isAvailable() {
        try {
            return resolveServices() != null;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    /** Returns the major API generation implemented by the installed normal mod. */
    public static int runtimeApiMajorVersion() {
        return services().apiMajorVersion();
    }

    /**
     * Returns the focused runtime services supplied by the installed Talking Colonists mod.
     * Static facade classes are conveniences over these same service objects.
     */
    public static @NotNull Services services() {
        Services current = resolveServices();
        if (current == null) {
            throw new IllegalStateException("Talking Colonists runtime is not available");
        }
        return current;
    }

    private static Services resolveServices() {
        Services current = services;
        if (current != null) return current;
        synchronized (TalkingColonistsApi.class) {
            current = services;
            if (current != null) return current;
            try {
                Class<?> implementation = Class.forName(
                        IMPLEMENTATION_CLASS,
                        true,
                        TalkingColonistsApi.class.getClassLoader()
                );
                Object instance = implementation.getField("INSTANCE").get(null);
                if (!(instance instanceof Services resolved)) {
                    throw new IllegalStateException(
                            "Installed Talking Colonists runtime does not implement the expected addon API services"
                    );
                }
                services = resolved;
                return resolved;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Talking Colonists runtime is not available; install the normal Talking Colonists mod",
                        e
                );
            }
        }
    }

    /** Aggregate of focused service contracts. Implementations are supplied only by the normal mod. */
    public interface Services {
        int apiMajorVersion();
        @NotNull PromptService prompts();
        @NotNull ConversationRuleService conversationRules();
        @NotNull PregenerationService pregeneration();
        @NotNull ToolService tools();
        @NotNull ContextService context();
        @NotNull ConversationService conversations();
        @NotNull MemoryService memory();
    }
}
