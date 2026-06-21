package me.sshcrack.mc_talking.api.provider;

import me.sshcrack.mc_talking.api.voice.VoiceDescriptor;

import java.util.List;
import java.util.Set;

public interface AiProvider {
    String id();
    String displayName();
    Set<Capability> capabilities();
    List<VoiceDescriptor> availableVoices();
}
