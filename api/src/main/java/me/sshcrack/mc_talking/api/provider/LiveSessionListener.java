package me.sshcrack.mc_talking.api.provider;

import java.util.List;

public interface LiveSessionListener {
    default void onGeneratedText(String text) {}

    default void onGeneratedAudio(AudioData audio) {}

    default void onTurnComplete() {}

    default void onInterrupted() {}

    default void onGenerationComplete() {}

    default void onInputTranscription(String text) {}

    default void onOutputTranscription(String text) {}

    default void onSessionResumptionUpdate(String newHandle, boolean resumable) {}

    default void onToolCall(ToolCall toolCall) {}

    default void onError(Throwable error) {}

    default void onClosed(int code, String reason) {}
}
