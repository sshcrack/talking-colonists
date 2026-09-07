package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.Nullable;

/**
 * A value together with how confidently/freshly it was observed.
 *
 * <p>A {@code CURRENT} zero, empty list, or empty inventory is a real observation and must not be
 * treated as unavailable. {@code observedAtGameTime} is the colony game tick when the snapshot was
 * assembled, or {@code -1} when no game clock was available.</p>
 */
public record ObservedValue<T>(
        ObservationState state,
        @Nullable T value,
        long observedAtGameTime
) {
    public ObservedValue {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        if (state == ObservationState.CURRENT && value == null) {
            throw new IllegalArgumentException("CURRENT observations require a value");
        }
    }

    public static <T> ObservedValue<T> current(T value, long gameTime) {
        return new ObservedValue<>(ObservationState.CURRENT, value, gameTime);
    }

    public static <T> ObservedValue<T> unavailable(ObservationState state, long gameTime) {
        if (state == ObservationState.CURRENT) throw new IllegalArgumentException("use current for current values");
        return new ObservedValue<>(state, null, gameTime);
    }
}
