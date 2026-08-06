package me.sshcrack.mc_talking.api.provider;

import java.util.Collection;
import java.util.Collections;

public interface AiProvider {
    String providerId();

    default String displayName() {
        return providerId();
    }

    Collection<Capability> capabilities();

    default boolean supports(Capability capability) {
        return capabilities().contains(capability);
    }

    default int priority() {
        return 0;
    }
}
