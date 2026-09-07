package dev.lattency.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        for (Object item : requireList(orDefault(root, "sinks", List.of()), "sinks")) {
            Map<?, ?> sink = requireMap(item, "sink");
            IoCategory category = parseCategory(sink.get("category"));
            sinks.add(new SinkDefinition(
                    parsePattern(requireMap(sink.get("match"), "match")), category));
        }

        List<String> exclusions = new ArrayList<>();
        for (Object exclusion : requireList(orDefault(root, "exclude", List.of()), "exclude")) {
            exclusions.add(requireString(exclusion, "exclude entry"));
        }

        Map<?, ?> ignore = requireMap(orDefault(root, "ignore", Map.of()), "ignore");
        Set<IoCategory> ignoredCategories = EnumSet.noneOf(IoCategory.class);
        for (Object category : requireList(
                orDefault(ignore, "categories", List.of()), "ignore.categories")) {
            ignoredCategories.add(parseCategory(category));
        }
        List<SinkPattern> ignoredSinks = new ArrayList<>();
        for (Object item : requireList(orDefault(ignore, "sinks", List.of()), "ignore.sinks")) {
            ignoredSinks.add(parsePattern(requireMap(item, "ignore.sinks entry")));
        }

        return new LattencyConfig(
                sinks, exclusions, parseDepth(root, warningLogger), ignoredCategories, ignoredSinks);
    }

    private static IoCategory parseCategory(Object value) {
        String name = requireString(value, "category");
        for (IoCategory category : IoCategory.values()) {
            if (category.name().equals(name)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown category '" + name + "'; expected one of "
                + Arrays.toString(IoCategory.values()));
    }

    /** One of the five match shapes, shared by {@code sinks[].match} and {@code ignore.sinks[]}. */
    private static SinkPattern parsePattern(Map<?, ?> match) {
        if (match.size() == 1 && match.containsKey("package")) {
            return SinkPattern.packagePrefix(requireString(match.get("package"), "package"));
        }
        if (match.size() == 1 && match.containsKey("class")) {
            return SinkPattern.className(requireString(match.get("class"), "class"));
        }
        if (match.size() == 2 && match.containsKey("class") && match.containsKey("method")) {
            return SinkPattern.method(
                    requireString(match.get("class"), "class"),
                    requireString(match.get("method"), "method"));
        }
        if (match.size() == 1 && match.containsKey("annotation")) {
            return SinkPattern.annotation(requireString(match.get("annotation"), "annotation"));
        }
        if (match.size() == 1 && match.containsKey("construction")) {
            return SinkPattern.construction(
                    requireString(match.get("construction"), "construction"));
        }
        throw new IllegalArgumentException("A sink match must contain package, class, "
                + "class + method, annotation, or construction");
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

    private static Object orDefault(Map<?, ?> map, String key, Object fallback) {
        return map.containsKey(key) ? map.get(key) : fallback;
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
