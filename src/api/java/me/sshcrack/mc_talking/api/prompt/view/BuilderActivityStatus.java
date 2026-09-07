package me.sshcrack.mc_talking.api.prompt.view;

/** Current high-level builder activity derived without a colony-wide scan. */
public enum BuilderActivityStatus {
    NOT_BUILDER,
    ACTIVE,
    WAITING_FOR_MATERIALS,
    SLEEPING,
    IDLE,
    UNKNOWN
}
