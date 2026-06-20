package me.sshcrack.mc_talking.api.provider;

import java.util.Map;
import java.util.Objects;

public final class ToolCall {
    private final String id;
    private final String name;
    private final Map<String, Object> args;

    public ToolCall(String id, String name, Map<String, Object> args) {
        this.id = Objects.requireNonNullElse(id, "");
        this.name = Objects.requireNonNull(name);
        this.args = args != null ? Map.copyOf(args) : Map.of();
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Map<String, Object> args() {
        return args;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ToolCall toolCall)) return false;
        return name.equals(toolCall.name) && id.equals(toolCall.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "ToolCall[id=" + id + ", name=" + name + "]";
    }
}
