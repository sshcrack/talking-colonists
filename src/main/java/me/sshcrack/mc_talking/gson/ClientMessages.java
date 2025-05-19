package me.sshcrack.mc_talking.gson;


import com.google.gson.Gson;

import javax.annotation.Nullable;

public class ClientMessages {
    @Nullable public BidiGenerateContentSetup setup;
    @Nullable public RealtimeInput realtime_input;

    public static String setup(BidiGenerateContentSetup set) {
        var gson = new Gson();
        var e = new ClientMessages();
        e.setup = set;

        return gson.toJson(e);
    }

    public static String input(RealtimeInput content) {
        var gson = new Gson();
        var e = new ClientMessages();
        e.realtime_input = content;

        return gson.toJson(e);
    }
}
