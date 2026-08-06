package me.sshcrack.mc_talking.api.provider;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class ToolDefinition {
    private final String name;
    private final String description;
    private final Map<String, Object> parameters;

    public ToolDefinition(String name, String description, Map<String, Object> parameters) {
        this.name = Objects.requireNonNull(name);
        this.description = Objects.requireNonNullElse(description, "");
        this.parameters = parameters != null
                ? Collections.unmodifiableMap(parameters)
                : Map.of();
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Map<String, Object> parameters() {
        return parameters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ToolDefinition that)) return false;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "ToolDefinition[name=" + name + "]";
    }
}
