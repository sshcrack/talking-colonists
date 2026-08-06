package me.sshcrack.mc_talking.session;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.provider.LiveSession;
import me.sshcrack.mc_talking.api.provider.LiveSessionListener;
import me.sshcrack.mc_talking.manager.GeminiStream;

import javax.annotation.Nullable;
import java.util.UUID;

public abstract class GeminiGameSession implements LiveSessionListener {
    protected final LiveSession liveSession;
    protected final AbstractEntityCitizen entity;
    protected final GeminiStream stream;

    protected boolean generationComplete = false;
    protected boolean shouldEndConversation = false;
    protected final StringBuilder sessionTranscript = new StringBuilder();

    public GeminiGameSession(LiveSession liveSession, AbstractEntityCitizen entity, GeminiStream stream) {
        this.liveSession = liveSession;
        this.entity = entity;
        this.stream = stream;
        liveSession.setListener(this);
    }

    public AbstractEntityCitizen getEntity() {
        return entity;
    }

    public void sendAudio(short[] audio) {
        liveSession.sendAudio(audio);
    }

    public void sendText(String text) {
        liveSession.sendText(text);
    }

    public void addPromptTextAfterTalkingComplete(String text) {
        liveSession.addPromptTextAfterTalkingComplete(text);
    }

    public void close() {
        liveSession.close();
    }

    public boolean isOpen() {
        return liveSession.isOpen();
    }

    public boolean isClosed() {
        return liveSession.isClosed();
    }

    public boolean isActive() {
        return liveSession.isActive();
    }

    public boolean isGenerationComplete() {
        return generationComplete;
    }

    @Nullable
    protected abstract UUID resolveActivePlayerId();
}
