package me.sshcrack.mc_talking.api;

/**
 * Additive addon-facing feature flags, one per roadmap "Track A" API task.
 *
 * <p>An addon compiled against a newer {@code mc_talking-api} artifact can run on an older
 * installed Talking Colonists runtime. Because the runtime is loaded from the installed mod jar
 * rather than the addon's compile-time API jar, calling a brand-new entry point that the running
 * mod does not implement yet fails with {@link NoSuchMethodError} or {@link NoClassDefFoundError}
 * rather than a friendly result. {@link TalkingColonistsApi#supports(ApiFeature)} lets an addon
 * check first and degrade gracefully instead.</p>
 *
 * <p>Every constant here is added the moment its task starts landing, generally long before the
 * feature's entry points exist. A constant only flips from unsupported to supported once its task
 * is fully implemented; until then {@link TalkingColonistsApi#supports(ApiFeature)} reports
 * {@code false} for it on every runtime, including the current development build.</p>
 *
 * <p>See {@code docs/addon-api.md} ("Feature detection") for the safe-on-2.0-runtimes calling
 * pattern, and {@link TalkingColonistsApi#requireSupported(ApiFeature)} for the standard failure
 * used by unsupported feature entry points.</p>
 */
public enum ApiFeature {
    /** Roadmap A1 — broadcast and news publishing. */
    BROADCAST_PUBLISHING,
    /** Roadmap A2 — colony event feed. */
    COLONY_EVENTS,
    /** Roadmap A3 — addon-triggered text generation. */
    TEXT_GENERATION,
    /** Roadmap A4 — player text input into conversations. */
    PLAYER_TEXT_INPUT,
    /** Roadmap A5 — utterance-level conversation events. */
    UTTERANCE_EVENTS,
    /** Roadmap A6 — player conversation options/menus. */
    PLAYER_CONVERSATION_OPTIONS,
    /** Roadmap A7 — provider capacity/quota budget API. */
    PROVIDER_BUDGET,
    /** Roadmap A8 — visitor (non-citizen) speakers. */
    VISITOR_SPEAKERS,
    /** Roadmap A9 — cross-colony sessions. */
    CROSS_COLONY_SESSIONS,
    /** Roadmap A10 — player speech capture. */
    PLAYER_SPEECH_CAPTURE,
    /** How far a published broadcast has spread: {@code CitizenMemoryService.broadcastReach}. */
    BROADCAST_REACH
}
