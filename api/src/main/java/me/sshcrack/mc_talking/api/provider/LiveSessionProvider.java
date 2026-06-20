package me.sshcrack.mc_talking.api.provider;

import java.util.Collection;
import java.util.List;

public interface LiveSessionProvider extends AiProvider {
    LiveSession createSession(LiveSessionConfig config);

    @Override
    default Collection<Capability> capabilities() {
        return List.of(Capability.LIVE_BUNDLE);
    }
}
