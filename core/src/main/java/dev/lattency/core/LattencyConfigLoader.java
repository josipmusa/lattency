package dev.lattency.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/** Loads the optional project-root {@code lattency.yml}. */
public final class LattencyConfigLoader {
    private LattencyConfigLoader() {}

    public static LattencyConfig load(Path path, Consumer<String> warningLogger) {
        if (!Files.isRegularFile(path)) {
            return LattencyConfig.defaultsOnly();
        }
        try (InputStream input = Files.newInputStream(path)) {
            Object document = new Load(LoadSettings.builder().build()).loadFromInputStream(input);
            return parse(document, warningLogger);
        } catch (RuntimeException | IOException exception) {
            warningLogger.accept("Could not read " + path + "; using built-in sinks: "
                    + exception.getMessage());
            return LattencyConfig.defaultsOnly();
        }
    }

    private static LattencyConfig parse(Object document, Consumer<String> warningLogger) {
        if (document == null) {
            return LattencyConfig.defaultsOnly();
        }
        Map<?, ?> root = requireMap(document, "root");
        List<SinkDefinition> sinks = new ArrayList<>();
        Object sinkItems = root.containsKey("sinks") ? root.get("sinks") : List.of();
        for (Object item : requireList(sinkItems, "sinks")) {
            Map<?, ?> sink = requireMap(item, "sink");
            IoCategory category = IoCategory.valueOf(requireString(sink.get("category"), "category"));
            Map<?, ?> match = requireMap(sink.get("match"), "match");
            if (match.size() == 1 && match.containsKey("package")) {
                sinks.add(SinkDefinition.packagePrefix(
                        requireString(match.get("package"), "package"), category));
            } else if (match.size() == 1 && match.containsKey("class")) {
                sinks.add(SinkDefinition.className(
                        requireString(match.get("class"), "class"), category));
            } else if (match.size() == 2
                    && match.containsKey("class")
                    && match.containsKey("method")) {
                sinks.add(SinkDefinition.method(
                        requireString(match.get("class"), "class"),
                        requireString(match.get("method"), "method"),
                        category));
            } else if (match.size() == 1 && match.containsKey("annotation")) {
                sinks.add(SinkDefinition.annotation(
                        requireString(match.get("annotation"), "annotation"), category));
            } else {
                throw new IllegalArgumentException("A sink match must contain package, class, "
                        + "class + method, or annotation");
            }
        }

        List<String> exclusions = new ArrayList<>();
        Object exclusionItems = root.containsKey("exclude") ? root.get("exclude") : List.of();
        for (Object exclusion : requireList(exclusionItems, "exclude")) {
            exclusions.add(requireString(exclusion, "exclude entry"));
        }
        return new LattencyConfig(sinks, exclusions, parseDepth(root, warningLogger));
    }

    private static int parseDepth(Map<?, ?> root, Consumer<String> warningLogger) {
        if (!root.containsKey("depth")) {
            return LattencyConfig.DEFAULT_DEPTH;
        }
        if (!(root.get("depth") instanceof Integer depth)) {
            throw new IllegalArgumentException("depth must be an integer");
        }
        if (depth < 0) {
            throw new IllegalArgumentException("depth must not be negative");
        }
        if (depth > LattencyConfig.MAX_DEPTH) {
            warningLogger.accept("Configured depth " + depth + " exceeds the hard cap; using "
                    + LattencyConfig.MAX_DEPTH);
            return LattencyConfig.MAX_DEPTH;
        }
        return depth;
    }

    private static Map<?, ?> requireMap(Object value, String field) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException(field + " must be a mapping");
    }

    private static List<?> requireList(Object value, String field) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new IllegalArgumentException(field + " must be a list");
    }

    private static String requireString(Object value, String field) {
        if (value instanceof String string && !string.isBlank()) {
            return string;
        }
        throw new IllegalArgumentException(field + " must be a non-empty string");
    }
}
