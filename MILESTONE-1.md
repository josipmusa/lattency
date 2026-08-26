# Milestone 1 — Direct sink detection, end to end

Read SPEC.md and AGENTS.md before doing anything. AGENTS.md rules are binding,
especially: verify every IntelliJ Platform API against the reference repos before
using it, and observe every feature working in the sandbox (`./gradlew runIde`)
before calling it done.

## Goal

Depth-0 version of Lattency: a gutter icon on the declaration of any method whose
body DIRECTLY contains a call matching a built-in or configured I/O sink, with a
category-specific icon and a tooltip explaining why. No transitive analysis in this
milestone.

## Explicitly out of scope (do not build these)

- Transitive propagation / call-graph walking of any kind
- Call-site marking (method declarations only)
- CachedValuesManager or any caching layer
- IDE settings UI
- Kotlin language support
- Lazy-JPA detection, @Cacheable handling, interface-implementation resolution
  (fixture placeholders only, see below)

## Tasks

### 1. Core module — sink model (pure Java, no com.intellij.* imports)

- [x] Sink definition model: matches by package prefix, fully-qualified class,
      class + method name, or annotation FQN; each sink has a category:
      DB, HTTP, MESSAGING, FILE, GENERIC.
- [x] Built-in default sink set covering (see SPEC.md §2):
      - Spring Data: any method on a type in the Spring Data Repository hierarchy
        (represented in core as a "supertype sink" the plugin module queries with
        a supertype list) → DB
      - org.springframework.web.client.RestClient, RestTemplate,
        org.springframework.web.reactive.function.client.WebClient,
        java.net.http.HttpClient, okhttp3.*, Feign clients → HTTP
      - javax.sql/java.sql (DataSource, Connection, Statement, PreparedStatement),
        JdbcTemplate, JdbcClient → DB
      - com.google.cloud.pubsub.v1.Publisher, org.apache.kafka.clients.producer.*,
        JmsTemplate, RabbitTemplate → MESSAGING
      - java.nio.file.Files, java.io file streams/readers/writers → FILE
      - org.jetbrains.annotations.Blocking annotation → GENERIC sink;
        org.jetbrains.annotations.NonBlocking → suppression (method is never marked)
- [x] YAML config: parse `lattency.yml` from project root; supports adding custom
      sinks (pattern + category) and excluding packages/classes. Missing file =
      defaults only. Malformed file = defaults + a logged warning, never a crash.
- [x] Unit tests for matching logic and YAML parsing (plain JUnit, no IDE).
- [x] ArchUnit test in core: no class in the core module imports com.intellij.*.

### 2. Fixture project (test-fixtures/)

Minimal Spring Boot project (does not need to actually run; it needs to compile and
resolve types), containing one clearly named class per case:

- [x] Detected in this milestone: a Spring Data repository call; a RestClient call;
      a JdbcTemplate call; a Kafka or Pub/Sub publish; a Files.readString; a method
      annotated @Blocking; a method annotated @NonBlocking that contains a sink call
      (must NOT be marked); a class matched by a custom sink in the fixture's
      lattency.yml; a class excluded via lattency.yml (must NOT be marked).
- [x] Placeholders for later milestones (present in code, expected UNMARKED for
      now, each with a `// lattency-future:` comment): a call chain 3 levels deep
      ending in a repository; an interface with two implementations where one does
      I/O; a @Cacheable method wrapping a repository call; a LAZY @ManyToOne getter
      traversal.

### 3. Plugin module — PSI → core translation

- [x] For each method call expression in a method body, extract: resolved method's
      containing class FQN, its supertype FQNs, method name, and annotations of the
      resolved method; pass these facts to core's matcher.
- [x] Spring Data detection MUST work via supertype check against
      org.springframework.data.repository.Repository, not via class-name matching.
- [x] Method-level @NonBlocking on the containing method suppresses marking of that
      method entirely.
- [x] Load lattency.yml from the project base dir; changes picked up without IDE
      restart (re-read on change; a file listener or per-analysis re-read is fine —
      pick the simplest verified pattern).

### 4. Line markers

- [x] Gutter icon on the METHOD NAME IDENTIFIER (leaf element, per AGENTS.md) of any
      method whose body directly contains ≥1 sink call.
- [x] Category-specific placeholder icons (simple colored glyphs are fine: DB, HTTP,
      MSG, FILE, IO). Multiple categories in one method → one combined/generic icon
      is acceptable for now.
- [x] Tooltip lists each detected sink call: `<callee> → [CATEGORY]`, one per line.
- [x] Fast pass stays cheap: filter to method-name identifiers before any resolve();
      follow AGENTS.md fast/slow pass guidance.
- [x] Dumb mode: return no markers, no exceptions.

### 5. Platform tests

- [x] Marker present/absent tests on fixture-style code for: each sink category,
      @Blocking (present), @NonBlocking suppression (absent), custom YAML sink
      (present), YAML exclusion (absent), and one future-placeholder case
      (absent — pins depth-0 behavior).
- [x] Verify the test base class choice against the reference repos before writing
      tests (this API area churns).

## Acceptance checklist (all must pass)

- [x] `./gradlew build` green: core unit tests, ArchUnit rule, platform tests.
- [x] Sandbox (`runIde`) with test-fixtures open: every "detected" case shows the
      correct category icon and a correct tooltip; every "must not be marked" case
      and every future placeholder shows nothing.
- [x] Editing a marked method to remove the sink call removes the icon after the
      edit (no restart); re-adding it brings the icon back.
- [x] idea.log from the sandbox session contains no exceptions from Lattency.
- [x] Typing in a large file with markers shows no perceptible editor lag.
- [x] README updated: one paragraph on what works after this milestone + a
      screenshot of the fixture project with markers.

## Working agreements for this milestone

- Commit per task section, not one mega-commit.
- If an IntelliJ API needed for a task can't be verified in the reference repos,
  stop and report instead of improvising.
- If any acceptance item can't be met, deliver the milestone with that item
  explicitly listed as failing and why — do not silently narrow scope.
