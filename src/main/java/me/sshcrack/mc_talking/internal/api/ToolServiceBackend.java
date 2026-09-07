package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.api.service.ToolService;
import me.sshcrack.mc_talking.api.tool.AiTool;
import me.sshcrack.mc_talking.internal.tool.AiToolRuntime;
import org.jetbrains.annotations.NotNull;

final class ToolServiceBackend implements ToolService {
    @Override
    public @NotNull AddonRegistration register(@NotNull String namespace, @NotNull String name,
                                                @NotNull AiTool tool) {
        return AiToolRuntime.register(namespace, name, tool);
    }
}
