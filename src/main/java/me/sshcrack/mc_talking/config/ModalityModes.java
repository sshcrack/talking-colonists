package me.sshcrack.mc_talking.config;

import java.util.List;

public enum ModalityModes {
    TEXT,
    AUDIO,
    TEXT_AND_AUDIO;

    public List<String> getModes() {
        return switch (this) {
            case TEXT -> List.of("TEXT");
            case AUDIO -> List.of("AUDIO");
            case TEXT_AND_AUDIO -> List.of("TEXT", "AUDIO");
        };
    }
}
