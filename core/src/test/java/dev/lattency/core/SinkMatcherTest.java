package dev.lattency.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SinkMatcherTest {
    private final SinkMatcher matcher = new SinkMatcher(LattencyConfig.defaultsOnly());

    @Test
    void matchesEveryDefinitionKind() {
        var configured = new SinkMatcher(new LattencyConfig(
                List.of(
                        SinkDefinition.packagePrefix("example.remote", IoCategory.HTTP),
                        SinkDefinition.className("example.Files", IoCategory.FILE),
                        SinkDefinition.method("example.Bus", "send", IoCategory.MESSAGING),
                        SinkDefinition.annotation("example.Blocking", IoCategory.GENERIC)),
                List.of(),
                LattencyConfig.DEFAULT_DEPTH,
                Set.of(),
                List.of()));

        assertEquals(IoCategory.HTTP, configured.match(facts("example.remote.Client", "get")).orElseThrow());
        assertEquals(IoCategory.FILE, configured.match(facts("example.Files", "read")).orElseThrow());
        assertEquals(IoCategory.MESSAGING, configured.match(facts("example.Bus", "send")).orElseThrow());
        assertEquals(
                IoCategory.GENERIC,
                configured.match(SinkFacts.ofCall("example.Work", Set.of(), "run", Set.of("example.Blocking")))
                        .orElseThrow());
    }

    @Test
    void matchesSpringDataOnlyThroughSupertypeFacts() {
        assertTrue(matcher.match(facts("example.OrderRepository", "save")).isEmpty());
        var facts = SinkFacts.ofCall(
                "example.OrderRepository",
                Set.of(BuiltInSinks.SPRING_DATA_REPOSITORY),
                "save",
                Set.of());
        assertEquals(IoCategory.DB, matcher.match(facts).orElseThrow());
    }

    @Test
    void coversBuiltInCategories() {
        assertEquals(IoCategory.HTTP, matcher.match(facts("okhttp3.Call", "execute")).orElseThrow());
        assertEquals(IoCategory.DB, matcher.match(facts("java.sql.Connection", "commit")).orElseThrow());
        assertEquals(
                IoCategory.MESSAGING,
                matcher.match(facts("org.apache.kafka.clients.producer.KafkaProducer", "send")).orElseThrow());
        assertEquals(IoCategory.FILE, matcher.match(facts("java.nio.file.Files", "readString")).orElseThrow());
    }

    @Test
    void matchesJdbcImplementationsThroughApiSupertypes() {
        var facts = SinkFacts.ofCall(
                "com.zaxxer.hikari.pool.HikariProxyConnection",
                Set.of("java.sql.Connection"),
                "commit",
                Set.of());

        assertEquals(IoCategory.DB, matcher.match(facts).orElseThrow());
    }

    @Test
    void exclusionsWinOverSinkRules() {
        var configured = new SinkMatcher(new LattencyConfig(
                List.of(), List.of("java.nio"), LattencyConfig.DEFAULT_DEPTH, Set.of(), List.of()));
        assertTrue(configured.match(facts("java.nio.file.Files", "readString")).isEmpty());
    }

    @Test
    void ignoredCategoryDropsBuiltInAndProjectSinksOfThatCategoryOnly() {
        var configured = new SinkMatcher(new LattencyConfig(
                List.of(SinkDefinition.className("example.Store", IoCategory.DB)),
                List.of(),
                LattencyConfig.DEFAULT_DEPTH,
                Set.of(IoCategory.DB),
                List.of()));

        assertTrue(configured.match(facts("java.sql.Connection", "commit")).isEmpty());
        assertTrue(configured.match(facts("example.Store", "save")).isEmpty());
        var repository = SinkFacts.ofCall(
                "example.OrderRepository", Set.of(BuiltInSinks.SPRING_DATA_REPOSITORY), "save", Set.of());
        assertTrue(configured.match(repository).isEmpty());
        assertEquals(IoCategory.HTTP, configured.match(facts("okhttp3.Call", "execute")).orElseThrow());
    }

    @Test
    void ignoredSinkKeepsTheRestOfItsCategory() {
        var configured = new SinkMatcher(new LattencyConfig(
                List.of(),
                List.of(),
                LattencyConfig.DEFAULT_DEPTH,
                Set.of(),
                List.of(
                        SinkPattern.method("java.nio.file.Files", "exists"),
                        SinkPattern.className("java.io.File"),
                        SinkPattern.packagePrefix("feign"),
                        SinkPattern.annotation(BuiltInSinks.BLOCKING),
                        SinkPattern.construction("java.io.FileInputStream"))));

        assertTrue(configured.match(facts("java.nio.file.Files", "exists")).isEmpty());
        assertEquals(IoCategory.FILE, configured.match(facts("java.nio.file.Files", "readString")).orElseThrow());
        assertTrue(configured.match(facts("java.io.File", "delete")).isEmpty());
        assertEquals(IoCategory.FILE, configured.match(facts("java.io.FileWriter", "write")).orElseThrow());
        assertTrue(configured.match(facts("feign.Client", "execute")).isEmpty());
        assertEquals(IoCategory.HTTP, configured.match(facts("okhttp3.Call", "execute")).orElseThrow());
        assertTrue(configured.match(
                SinkFacts.ofCall("example.Work", Set.of(), "run", Set.of(BuiltInSinks.BLOCKING))).isEmpty());
        // Ignoring construction leaves the type's methods as sinks, mirroring the sink rules.
        assertTrue(configured.match(
                SinkFacts.ofConstruction("java.io.FileInputStream", Set.of(), Set.of())).isEmpty());
        assertEquals(IoCategory.FILE, configured.match(facts("java.io.FileInputStream", "read")).orElseThrow());
    }

    @Test
    void ignoredSinkWinsOverProjectSinkRules() {
        var configured = new SinkMatcher(new LattencyConfig(
                List.of(SinkDefinition.className("example.Store", IoCategory.DB)),
                List.of(),
                LattencyConfig.DEFAULT_DEPTH,
                Set.of(),
                List.of(SinkPattern.method("example.Store", "ping"))));

        assertTrue(configured.match(facts("example.Store", "ping")).isEmpty());
        assertEquals(IoCategory.DB, configured.match(facts("example.Store", "save")).orElseThrow());
    }

    @Test
    void constructionMatchesOnlyConstructionRules() {
        // Opening a stream is the file access...
        assertEquals(
                IoCategory.FILE,
                matcher.match(SinkFacts.ofConstruction("java.io.FileInputStream", Set.of(), Set.of()))
                        .orElseThrow());
        // ...but merely naming a file, or wrapping an existing reader, is not.
        assertTrue(matcher.match(SinkFacts.ofConstruction("java.io.File", Set.of(), Set.of())).isEmpty());
        assertTrue(
                matcher.match(SinkFacts.ofConstruction("java.io.BufferedReader", Set.of(), Set.of()))
                        .isEmpty());
        // A type-shaped rule describes an API surface, so it must not fire on construction:
        // new ProducerRecord<>(..) is not a message being published.
        assertTrue(
                matcher.match(SinkFacts.ofConstruction(
                                "org.apache.kafka.clients.producer.ProducerRecord", Set.of(), Set.of()))
                        .isEmpty());
    }

    @Test
    void annotationRulesApplyToConstructorsToo() {
        assertEquals(
                IoCategory.GENERIC,
                matcher.match(SinkFacts.ofConstruction(
                                "example.Work", Set.of(), Set.of(BuiltInSinks.BLOCKING)))
                        .orElseThrow());
    }

    private static SinkFacts facts(String className, String methodName) {
        return SinkFacts.ofCall(className, Set.of(), methodName, Set.of());
    }
}
