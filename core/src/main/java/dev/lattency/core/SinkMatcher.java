package dev.lattency.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Matches resolved call facts against built-in and project sink definitions. */
public final class SinkMatcher {
    private final List<SinkDefinition> definitions;
    private final List<String> exclusions;

    public SinkMatcher(LattencyConfig config) {
        definitions = new ArrayList<>(BuiltInSinks.definitions());
        definitions.addAll(config.sinks());
        exclusions = config.exclusions();
    }

    public Optional<IoCategory> match(SinkFacts facts) {
        if (isExcluded(facts.containingClassFqn())) {
            return Optional.empty();
        }
        return definitions.stream()
                .filter(definition -> matches(definition, facts))
                .map(SinkDefinition::category)
                .findFirst();
    }

    public boolean isExcluded(String classFqn) {
        return exclusions.stream().anyMatch(pattern ->
                classFqn.equals(pattern) || classFqn.startsWith(pattern + "."));
    }

    private static boolean matches(SinkDefinition definition, SinkFacts facts) {
        boolean construction = facts.target() == SinkFacts.Target.CONSTRUCTION;
        return switch (definition.kind()) {
            // Type-shaped rules describe an API surface; constructing the type is not
            // calling it (see SinkFacts).
            case PACKAGE_PREFIX -> !construction
                    && (facts.containingClassFqn().equals(definition.pattern())
                            || facts.containingClassFqn().startsWith(definition.pattern() + "."));
            case CLASS -> !construction
                    && facts.containingClassFqn().equals(definition.pattern());
            case METHOD -> !construction
                    && facts.containingClassFqn().equals(definition.pattern())
                    && facts.methodName().equals(definition.methodName());
            case SUPERTYPE -> !construction
                    && facts.supertypeFqns().contains(definition.pattern());
            // Annotations name the target itself, so they apply to constructors too.
            case ANNOTATION -> facts.annotationFqns().contains(definition.pattern());
            case CONSTRUCTION -> construction
                    && facts.containingClassFqn().equals(definition.pattern());
        };
    }
}
