package me.sshcrack.mc_talking.api.conversation;

/** Why core is considering a citizen for speech. Addon policies can selectively veto kinds. */
public enum ConversationKind {
    PLAYER,
    MUMBLE,
    URGENT_CONTACT,
    RANDOM_CITIZEN,
    CITIZEN_PAIR,
    PREGENERATED,
    ADDON_AMBIENT,
    CONTROLLED
}
