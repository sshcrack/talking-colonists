package me.sshcrack.mc_talking.config;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

public enum AvailableAI implements NameableEnum {
    //Voice 'Fenir' isn't available for the API IDK why

    @SuppressWarnings({"unused", "SpellCheckingInspection"})
    Flash3("gemini-3.1-flash-live-preview",
            List.of("Puck", "Charon", "Orus", "Enceladus", "Iapetus", "Umbriel", "Algieba", "Algenib", "Rasalgethi", "Alnilam", "Schedar", "Archid", "Zubenelgenubi", "Sadachbia", "Sadaltager"),
            List.of("Zephyr", "Kore", "Leda", "Aoede", "Callirrhoe", "Autonoe", "Despina", "Erinome", "Laomedeia", "Achernar", "Gacrux", "Pulcherrima", "Vindemiatrix", "Sulafat")
    ),
    @SuppressWarnings("SpellCheckingInspection")
    Flash2_5("gemini-live-2.5-flash-native-audio",
            List.of("Puck", "Charon", "Orus", "Enceladus", "Iapetus", "Umbriel", "Algieba", "Algenib", "Rasalgethi", "Alnilam", "Schedar", "Archid", "Zubenelgenubi", "Sadachbia", "Sadaltager"),
            List.of("Zephyr", "Kore", "Leda", "Aoede", "Callirrhoe", "Autonoe", "Despina", "Erinome", "Laomedeia", "Achernar", "Gacrux", "Pulcherrima", "Vindemiatrix", "Sulafat")
    );

    private final String name;
    private final List<String> femaleVoices;
    private final List<String> maleVoices;

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

        var rng = new Random(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
        return availableVoices.get(rng.nextInt(availableVoices.size()));
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(name());
    }
}
