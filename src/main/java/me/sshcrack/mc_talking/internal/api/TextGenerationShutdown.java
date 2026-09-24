package me.sshcrack.mc_talking.internal.api;

/** Server-stop hook for pending addon text generation. */
public final class TextGenerationShutdown {
    private TextGenerationShutdown() {
    }

    public static void cancelAll() {
        TextServiceBackend.RUNTIME.cancelAll("The server is stopping");
    }
}
