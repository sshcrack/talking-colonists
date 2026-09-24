package me.sshcrack.mc_talking.config;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;

/**
 * Applies {@link ConfigPreset}s to a config object by field name. Works on any object with
 * the preset's fields plus {@code configPreset} and {@code appliedConfigPreset}, so tests
 * can use a plain class instead of {@link McTalkingConfig}.
 */
public final class ConfigPresets {
    static final String PRESET_FIELD = "configPreset";
    static final String APPLIED_FIELD = "appliedConfigPreset";

    private ConfigPresets() {
    }

    /** Writes every value of the preset. {@link ConfigPreset#CUSTOM} writes nothing. */
    public static void apply(Object config, ConfigPreset preset) {
        for (Map.Entry<String, Object> entry : preset.settings().entrySet()) {
            write(config, entry.getKey(), entry.getValue());
        }
    }

    /** Whether every value the preset sets currently has the preset's value. */
    public static boolean matches(Object config, ConfigPreset preset) {
        for (Map.Entry<String, Object> entry : preset.settings().entrySet()) {
            if (!Objects.equals(read(config, entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Brings the preset fields in line after the config was loaded or saved:
     * <ul>
     *   <li>a newly chosen preset (differs from the last applied one) writes its values;</li>
     *   <li>otherwise, if a preset value was edited, the preset becomes {@link ConfigPreset#CUSTOM}.</li>
     * </ul>
     *
     * @return whether anything changed and the config should be saved
     */
    public static boolean reconcile(Object config) {
        ConfigPreset chosen = (ConfigPreset) read(config, PRESET_FIELD);
        ConfigPreset applied = (ConfigPreset) read(config, APPLIED_FIELD);
        if (chosen == null) {
            chosen = ConfigPreset.CUSTOM;
            write(config, PRESET_FIELD, chosen);
        }

        if (chosen != applied) {
            apply(config, chosen);
            write(config, APPLIED_FIELD, chosen);
            return true;
        }
        if (chosen != ConfigPreset.CUSTOM && !matches(config, chosen)) {
            write(config, PRESET_FIELD, ConfigPreset.CUSTOM);
            write(config, APPLIED_FIELD, ConfigPreset.CUSTOM);
            return true;
        }
        return false;
    }

    /** The preset whose values all match, or {@link ConfigPreset#CUSTOM}. */
    public static ConfigPreset detect(Object config) {
        for (ConfigPreset preset : ConfigPreset.values()) {
            if (preset != ConfigPreset.CUSTOM && matches(config, preset)) {
                return preset;
            }
        }
        return ConfigPreset.CUSTOM;
    }

    static Object read(Object config, String name) {
        try {
            return field(config, name).get(config);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read config field " + name, e);
        }
    }

    static void write(Object config, String name, Object value) {
        try {
            field(config, name).set(config, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot write config field " + name, e);
        }
    }

    private static Field field(Object config, String name) {
        try {
            return config.getClass().getField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Config has no public field " + name, e);
        }
    }
}
