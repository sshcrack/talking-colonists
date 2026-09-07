package me.sshcrack.mc_talking.api.registration;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical validated identifier used by addon registrations, tools and activity owners. */
public record NamespacedAddonId(@NotNull String namespace, @NotNull String name) {
    private static final Pattern PART = Pattern.compile("[a-z][a-z0-9_]{0,31}");

    public NamespacedAddonId {
        namespace = requirePart(namespace, "namespace");
        name = requirePart(name, "name");
    }

    public static @NotNull NamespacedAddonId parse(@NotNull String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':') || separator == value.length() - 1) {
            throw new IllegalArgumentException("addon id must use namespace:name syntax: " + value);
        }
        return new NamespacedAddonId(value.substring(0, separator), value.substring(separator + 1));
    }

    public static @NotNull String require(@NotNull String value, @NotNull String label) {
        Objects.requireNonNull(label, "label");
        try {
            return parse(value).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label + " must use namespace:name with each part matching "
                    + PART.pattern() + ": " + value, e);
        }
    }

    private static String requirePart(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!PART.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must match " + PART.pattern() + ": " + value);
        }
        return value;
    }

    @Override
    public @NotNull String toString() {
        return namespace + ":" + name;
    }
}
