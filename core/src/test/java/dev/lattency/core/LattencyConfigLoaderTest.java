package dev.lattency.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
        assertEquals(SinkPattern.Kind.METHOD, config.sinks().getFirst().kind());
        assertEquals("fetch", config.sinks().getFirst().methodName());
        assertEquals(java.util.List.of("com.acme.generated"), config.exclusions());
        assertEquals(Set.of(), config.ignoredCategories());
        assertEquals(List.of(), config.ignoredSinks());
    }

    @Test
    void parsesIgnoredCategoriesAndSinks() throws IOException {
        Path configFile = directory.resolve("lattency.yml");
        Files.writeString(configFile, """
                ignore:
                  categories:
                    - DB
                    - MESSAGING
                  sinks:
                    - class: java.nio.file.Files
                      method: exists
                    - package: okhttp3
                    - annotation: org.jetbrains.annotations.Blocking
                    - construction: java.io.FileInputStream
                    - class: java.io.File
                """);

        LattencyConfig config = LattencyConfigLoader.load(configFile, ignored -> {});

        assertEquals(Set.of(IoCategory.DB, IoCategory.MESSAGING), config.ignoredCategories());
        assertEquals(
                List.of(
                        SinkPattern.method("java.nio.file.Files", "exists"),
                        SinkPattern.packagePrefix("okhttp3"),
                        SinkPattern.annotation("org.jetbrains.annotations.Blocking"),
                        SinkPattern.construction("java.io.FileInputStream"),
                        SinkPattern.className("java.io.File")),
                config.ignoredSinks());
    }

    @Test
    void unknownIgnoredCategoryIsMalformedAndNamesTheValidOnes() throws IOException {
        Path configFile = directory.resolve("lattency.yml");
        Files.writeString(configFile, """
                ignore:
                  categories: [DATABASE]
                """);
        var warnings = new ArrayList<String>();

        LattencyConfig config = LattencyConfigLoader.load(configFile, warnings::add);

        assertEquals(LattencyConfig.defaultsOnly(), config);
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("Unknown category 'DATABASE'"), warnings.getFirst());
        assertTrue(warnings.getFirst().contains("[DB, HTTP, MESSAGING, FILE, GENERIC]"),
                warnings.getFirst());
    }

    @Test
    void ignoredSinkWithUnknownShapeIsMalformed() throws IOException {
        Path configFile = directory.resolve("lattency.yml");
        Files.writeString(configFile, """
                ignore:
                  sinks:
                    - method: exists
                """);
        var warnings = new ArrayList<String>();

        LattencyConfig config = LattencyConfigLoader.load(configFile, warnings::add);

        assertEquals(LattencyConfig.defaultsOnly(), config);
        assertEquals(1, warnings.size());
    }

    @Test
    void ignoreThatIsNotAMappingIsMalformed() throws IOException {
        Path configFile = directory.resolve("lattency.yml");
        Files.writeString(configFile, "ignore: [DB]");
        var warnings = new ArrayList<String>();

        LattencyConfig config = LattencyConfigLoader.load(configFile, warnings::add);

        assertEquals(LattencyConfig.defaultsOnly(), config);
        assertEquals(1, warnings.size());
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
    void parsesUnsavedTextWithoutAFile() {
        LattencyConfig config = LattencyConfigLoader.parse("""
                depth: 2
                ignore:
                  categories: [HTTP]
                """, ignored -> {});

        assertEquals(2, config.depth());
        assertEquals(Set.of(IoCategory.HTTP), config.ignoredCategories());
    }

    @Test
    void invalidTextLogsWarningAndUsesDefaults() {
        var warnings = new ArrayList<String>();

        LattencyConfig config = LattencyConfigLoader.parse("ignore: [DB]", warnings::add);

        assertEquals(LattencyConfig.defaultsOnly(), config);
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("using built-in sinks"), warnings.getFirst());
        assertTrue(warnings.getFirst().contains("ignore must be a mapping"), warnings.getFirst());
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
