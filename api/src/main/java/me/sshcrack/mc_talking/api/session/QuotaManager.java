package me.sshcrack.mc_talking.api.session;

@FunctionalInterface
public interface QuotaManager {
    boolean tryConsume(String resourceKey);

    default void reportSuccess() {}

    default void reportFailure(Throwable error) {}
}
