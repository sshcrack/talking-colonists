package me.sshcrack.mc_talking.session;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.provider.LiveSession;
import me.sshcrack.mc_talking.manager.GeminiStream;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

public class CitizenGameSession extends GeminiGameSession {
    @Nullable
    private final ServerPlayer player;

    public CitizenGameSession(LiveSession liveSession, AbstractEntityCitizen entity, GeminiStream stream) {
        this(liveSession, entity, stream, null);
    }

    public CitizenGameSession(LiveSession liveSession, AbstractEntityCitizen entity, GeminiStream stream, @Nullable ServerPlayer player) {
        super(liveSession, entity, stream);
        this.player = player;
    }

    public boolean isPlayerConversation() {
        return player != null;
    }

    @Nullable
    public ServerPlayer getPlayer() {
        return player;
    }

    @Override
    @Nullable
    protected UUID resolveActivePlayerId() {
        return player != null ? player.getUUID() : null;
    }
}
