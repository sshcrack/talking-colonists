package me.sshcrack.mc_talking.config;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public enum AvailableAI {
    //Voice 'Fenir' isn't available for the API IDK why

    @SuppressWarnings("unused")
    Flash2_0("gemini-2.0-flash-live-001", List.of("Puck", "Charon", "Orus"), List.of("Kore", "Aoede", "Leda", "Zephyr")),
    @SuppressWarnings("unused")
    Flash2_5("gemini-2.5-flash-preview-native-audio-dialog",
            List.of("Puck", "Charon", "Orus", "Enceladus", "Iapetus", "Umbriel", "Algieba", "Algenib", "Rasalgethi", "Alnilam", "Schedar", "Archid", "Zubenelgenubi", "Sadachbia", "Sadaltager"),
            List.of("Zephyr", "Kore", "Leda", "Aoede", "Callirrhoe", "Autonoe", "Despina", "Erinome", "Laomedeia", "Achernar", "Gacrux", "Pulcherrima", "Vindemiatrix", "Sulafat")
    );

    private final String name;
    private final List<String> femaleVoices;
    private final List<String> maleVoices;
    public static final Random random = new Random();

    AvailableAI(String name, List<String> maleVoices, List<String> femaleVoices) {
        this.name = name;
        this.femaleVoices = femaleVoices;
        this.maleVoices = maleVoices;
    }

    public String getName() {
        return name;
    }

    public String getRandomVoice(UUID uuid, boolean isFemale) {
        var availableVoices = isFemale ? femaleVoices : maleVoices;

        random.setSeed(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
        return availableVoices.get(random.nextInt(availableVoices.size()));
    }
}
