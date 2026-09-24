package me.sshcrack.mc_talking.testing;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Compares rendered prompts with readable text snapshots in {@code src/test/resources/prompt-snapshots/}.
 *
 * <p>Regenerate after an intentional prompt change with
 * {@code UPDATE_PROMPT_SNAPSHOTS=1 ./gradlew :1.21.1-neoforge:test --tests '*PromptSnapshotTest' --rerun},
 * then review the diff like any other change.</p>
 */
public final class PromptSnapshots {
    private static final String DIRECTORY = "prompt-snapshots";
    private static final String REGENERATE =
            "UPDATE_PROMPT_SNAPSHOTS=1 ./gradlew :1.21.1-neoforge:test --tests '*PromptSnapshotTest' --rerun";

    private PromptSnapshots() {
    }

    public static void assertMatches(String name, String actual) {
        String normalized = normalize(actual);
        if ("1".equals(System.getenv("UPDATE_PROMPT_SNAPSHOTS"))) {
            write(name, normalized);
            return;
        }

        String expected = read(name);
        if (expected == null) {
            fail("Missing prompt snapshot " + name + ".txt; create it with: " + REGENERATE);
        }
        assertEquals(expected, normalized,
                "Prompt snapshot " + name + ".txt differs. If the change is intended, regenerate with: " + REGENERATE);
    }

    private static String normalize(String text) {
        String unix = text.replace("\r\n", "\n");
        return unix.endsWith("\n") ? unix : unix + "\n";
    }

    private static String read(String name) {
        try (InputStream in = PromptSnapshots.class.getResourceAsStream("/" + DIRECTORY + "/" + name + ".txt")) {
            return in == null ? null : normalize(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(String name, String content) {
        Path file = sourceDirectory().resolve(name + ".txt");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Tests run from {@code versions/<version>}; snapshots live in the shared root source tree. */
    private static Path sourceDirectory() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("stonecutter.gradle.kts"))) {
            dir = dir.getParent();
        }
        if (dir == null) throw new IllegalStateException("Could not find the repository root from " + Path.of("").toAbsolutePath());
        return dir.resolve("src/test/resources").resolve(DIRECTORY);
    }
}
