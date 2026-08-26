package dev.lattency.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LattencyConfigLoaderTest {
    @TempDir Path directory;

    @Test
    void missingFileMeansDefaultsOnly() {
        assertEquals(
                LattencyConfig.defaultsOnly(),
                LattencyConfigLoader.load(directory.resolve("lattency.yml"), ignored -> {}));
    }

    @Test
    void parsesCustomSinksAndExclusions() throws IOException {
        Path configFile = directory.resolve("lattency.yml");
        Files.writeString(configFile, """
                sinks:
                  - match:
                      class: com.acme.RemoteStore
                      method: fetch
                    category: DB
                  - match:
                      annotation: com.acme.Networked
                    category: HTTP
                exclude:
                  - com.acme.generated
                """);

        LattencyConfig config = LattencyConfigLoader.load(configFile, ignored -> {});

        assertEquals(2, config.sinks().size());
        assertEquals(SinkDefinition.Kind.METHOD, config.sinks().getFirst().kind());
        assertEquals("fetch", config.sinks().getFirst().methodName());
        assertEquals(java.util.List.of("com.acme.generated"), config.exclusions());
    }

    @Test
    void missingDepthMeansDefaultDepth() throws IOException {
        Path configFile = directory.resolve("lattency.yml");
        Files.writeString(configFile, """
                exclude:
                  - com.acme.generated
                """);

        LattencyConfig config = LattencyConfigLoader.load(configFile, ignored -> {});

        assertEquals(LattencyConfig.DEFAULT_DEPTH, config.depth());
        assertEquals(4, LattencyConfig.DEFAULT_DEPTH);
    }

    @Test
    void parsesConfiguredDepth() throws IOException {
        Path configFile = directory.resolve("lattency.yml");
        Files.writeString(configFile, "depth: 2");

        assertEquals(2, LattencyConfigLoader.load(configFile, ignored -> {}).depth());
    }

    @Test
    void clampsDepthToTheHardCapWithAWarning() throws IOException {
        Path configFile = directory.resolve("lattency.yml");
        Files.writeString(configFile, "depth: 50");
        var warnings = new ArrayList<String>();

        LattencyConfig config = LattencyConfigLoader.load(configFile, warnings::add);

        assertEquals(LattencyConfig.MAX_DEPTH, config.depth());
        assertEquals(10, LattencyConfig.MAX_DEPTH);
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("depth"));
    }

    @Test
    void negativeDepthIsMalformed() throws IOException {
        Path configFile = directory.resolve("lattency.yml");
        Files.writeString(configFile, "depth: -1");
        var warnings = new ArrayList<String>();

        LattencyConfig config = LattencyConfigLoader.load(configFile, warnings::add);

        assertEquals(LattencyConfig.defaultsOnly(), config);
        assertEquals(1, warnings.size());
    }

    @Test
    void nonNumericDepthIsMalformed() throws IOException {
        Path configFile = directory.resolve("lattency.yml");
        Files.writeString(configFile, "depth: shallow");
        var warnings = new ArrayList<String>();

        LattencyConfig config = LattencyConfigLoader.load(configFile, warnings::add);

        assertEquals(LattencyConfig.defaultsOnly(), config);
        assertEquals(1, warnings.size());
    }

    @Test
    void malformedFileLogsWarningAndUsesDefaults() throws IOException {
        Path configFile = directory.resolve("lattency.yml");
        Files.writeString(configFile, "sinks: definitely-not-a-list");
        var warnings = new ArrayList<String>();

        LattencyConfig config = LattencyConfigLoader.load(configFile, warnings::add);

        assertEquals(LattencyConfig.defaultsOnly(), config);
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("using built-in sinks"));
    }
}
