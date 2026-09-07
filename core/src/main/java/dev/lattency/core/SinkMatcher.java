package dev.lattency.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Matches resolved call facts against built-in and project sink definitions. */
public final class SinkMatcher {
    private final List<SinkDefinition> definitions;
    private final List<String> exclusions;
    private final List<SinkPattern> ignoredSinks;

    public SinkMatcher(LattencyConfig config) {
        List<SinkDefinition> all = new ArrayList<>(BuiltInSinks.definitions());
        all.addAll(config.sinks());
        definitions = all.stream()
                .filter(definition -> !config.ignoredCategories().contains(definition.category()))
                .toList();
        exclusions = config.exclusions();
        ignoredSinks = config.ignoredSinks();
    }

    public Optional<IoCategory> match(SinkFacts facts) {
        if (isExcluded(facts.containingClassFqn())
                || ignoredSinks.stream().anyMatch(ignored -> ignored.matches(facts))) {
            return Optional.empty();
        }
        return definitions.stream()
                .filter(definition -> definition.shape().matches(facts))
                .map(SinkDefinition::category)
                .findFirst();
    }

    public boolean isExcluded(String classFqn) {
        return exclusions.stream().anyMatch(pattern ->
                classFqn.equals(pattern) || classFqn.startsWith(pattern + "."));
    }
}
