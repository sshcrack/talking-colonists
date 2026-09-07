package me.sshcrack.mc_talking.api.service;

import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.api.tool.AiTool;
import org.jetbrains.annotations.NotNull;

/** Runtime service for addon AI-tool registrations. */
public interface ToolService {
    @NotNull AddonRegistration register(@NotNull String namespace, @NotNull String name, @NotNull AiTool tool);
}
