package me.sshcrack.mc_talking.gson;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ClientContent {
    public boolean turn_complete;
    public List<Turn> turns = new ArrayList<>();

    public static class Turn {
        public final String role = "user";
        public List<Part> parts = new ArrayList<>();

        public static class Part {
            @NotNull public String text;
            public Part(@NotNull String text) {
                this.text = text;
            }
        }
    }
}
