# SPEC — Lattency

An IntelliJ IDEA plugin that visually marks code which performs I/O — network, database,
messaging, file system — directly or transitively, so a reviewer can see at a glance where
a method "leaves the process" without clicking through the call chain.

Primary user: a developer reviewing PRs with the branch open in IDEA (Java / Spring Boot
codebases). The product is passive and ambient: no actions to invoke, no windows to open.
The signal lives in the editor gutter and on call sites.

---

## 1. Core concept

- A method is **I/O-colored** if its body directly performs I/O, or if it calls
  (transitively, up to a configurable depth) a method that does.
- The product should compute this statically — no running application, no agents,
  no runtime data.
- The product should distinguish **I/O categories** and reflect them visually:
  - Database (DB)
  - HTTP / web API
  - Messaging (publish/subscribe, queues)
  - File system
  - Unknown/generic I/O
- A method colored only transitively should be visually distinguishable from a method
  that performs I/O directly.

## 2. What the product should detect (sinks)

Out of the box, with zero configuration, on a typical Spring Boot project:

- **The product should trace JPA / Spring Data repository calls and mark them as DB I/O.**
  Any call to a method on an interface extending Spring Data's repository hierarchy is a
  DB sink by definition (no implementation exists to analyze).
- **The product should identify web/HTTP API calls and mark them as HTTP I/O**:
  RestClient, RestTemplate, WebClient, java.net.http.HttpClient, OkHttp, Feign clients.
- **The product should identify JDBC usage and mark it as DB I/O**:
  DataSource, Connection, JdbcTemplate/JdbcClient, raw Statement/PreparedStatement.
- **The product should identify messaging calls and mark them as messaging I/O**:
  Google Cloud Pub/Sub publisher clients, Kafka producers, JMS/RabbitMQ templates.
- **The product should identify file system access and mark it as file I/O**:
  java.io / java.nio file operations.
- **The product should respect JetBrains `@Blocking` / `@NonBlocking` annotations**:
  `@Blocking` methods count as sinks; `@NonBlocking` cuts propagation.
- **The product should treat `@Cacheable` (and similar caching annotations) as
  "conditional I/O"** and mark it distinctly from unconditional I/O.
- **The product should flag JPA lazy-association traversal as potential DB I/O**
  (best effort): calling a getter for a LAZY @ManyToOne/@OneToMany association on a
  managed entity may trigger a query. Approximate detection is acceptable; this may
  ship after v1.

## 3. Configuration

- **The product should work with zero config** using built-in sink definitions for the
  common Java/Spring stack.
- **The product should support a per-project config file committed to the repo**
  (e.g. `io-sinks.yml` at project root) that can:
  - add custom sinks by package, class, method, or annotation pattern;
  - assign a category to each custom sink;
  - exclude packages/classes from analysis or from marking (e.g. test sources);
  - set the transitive propagation depth (with a sane bounded default).
- Config changes should take effect without restarting the IDE.

## 4. Presentation

- **The product should show a gutter icon on the declaration of every I/O-colored
  method**, with the icon reflecting the category (or a combined icon for multiple
  categories).
- **The product should mark call sites**: an invocation of an I/O-colored method is
  visually indicated on that line (gutter icon or inlay hint — implementation's choice).
- **The tooltip on a marker should answer "why"**: the sink chain, e.g.
  `processOrder → orderRepository.save → [DB]`, and the category per chain.
- **Clicking a marker should navigate**: to the sink call site (or show the chain and
  let the user jump to any link in it).
- The user should be able to toggle marking off/on quickly (standard gutter icon
  enable/disable is acceptable).

## 5. Scope and boundaries

- Languages: Java first. Kotlin support is desirable later; nothing in the design
  should preclude it.
- The analysis stops at async boundaries: publishing a message is the sink; whatever
  consumes it is out of scope.
- Third-party library internals are not analyzed; libraries are covered via sink
  definitions (their API surface), not by walking their bytecode.
- Test sources are excluded from marking by default.
- False negatives are acceptable; noisy false positives are the bigger sin. When in
  doubt, prefer marking less with a clear "why" over marking more.

## 6. Performance expectations

- Marking must never noticeably degrade editor responsiveness; analysis is incremental
  and cached, recomputed only for code that changed.
- The plugin must behave correctly during indexing ("dumb mode"): show nothing rather
  than wrong things, and recover automatically.

## 7. Non-goals (v1)

- No runtime measurement, profiling, or OTEL integration.
- No CI / CLI / GitHub Action frontend (the core model should not preclude one).
- No reactive-context correctness checking (that's IntelliJ's built-in inspection's job).
- No quick-fixes or refactorings; this is a lens, not a linter.
- No IDE settings UI beyond the minimum; the repo config file is the source of truth.

## 8. Open source

- The repo should be structured as: a **core model** module (sink taxonomy, config
  schema/parsing, coloring model — no IntelliJ imports) and an **IntelliJ adapter**
  module (PSI resolution, markers, caching).
- Published to JetBrains Marketplace once stable enough for daily self-use.

## 9. Success criteria

- On the author's real projects, opening a PR branch shows correct markers on the
  changed files with no perceptible lag.
- During a real review, the markers answer "does this change add or move I/O?"
  without manually walking the call chain.
- Zero-config experience on a fresh Spring Boot project is already useful.