# AGENTS.md — guidance for AI coding agents on this project

This project is an IntelliJ Platform plugin. This is a domain where coding agents
reliably hallucinate APIs. Read this file fully before writing code. When this file
conflicts with your instinct, this file wins.

## The #1 rule: never invent IntelliJ Platform APIs

The IntelliJ SDK churns between releases. Your training data mixes API eras
(2019–2024) and contains plausible-sounding classes and extension points that do not
exist or are deprecated. Therefore:

- **Never call a platform API from memory alone.** Before using any class/method from
  `com.intellij.*`, verify it exists in THIS project's resolved SDK: check the external
  libraries the Gradle plugin downloaded, or grep the reference repos (below).
- If you cannot verify an API, say so and look it up — do not guess a similar name.
- Deprecation warnings are errors for us: find the current replacement, don't suppress.

## Pinned versions — do not drift

- Target platform: **IntelliJ IDEA 2025.2** (see `gradle.properties` for the exact
  build). Do not change platform/plugin versions to "fix" a compile error without
  explaining why.
- Build system: **intellij-platform-gradle-plugin (2.x)**. Do NOT use the legacy
  `org.jetbrains.intellij` (1.x) plugin, and do not mix docs/snippets between the two —
  their DSLs differ and 1.x examples will not work.
- Language: Java for production code (Kotlin allowed only if a platform API forces it;
  isolate it). JDK version: whatever `build.gradle.kts` toolchain says.

## Reference repos are the source of truth for patterns

Clone-if-missing into `../references/` and grep them before implementing any platform
integration point:

- `JetBrains/intellij-sdk-code-samples` — canonical minimal examples
  (line markers, inspections, services, settings).
- `JetBrains/intellij-community` — the platform source itself; the ultimate answer to
  "what does this API actually do" and "how does the platform's own code use it".
- `digma-ai/digma-intellij-plugin` — a real production plugin doing editor markers,
  caching, and background analysis.

Pattern to follow: find how the sample/production code registers and implements the
thing, copy that shape, adapt. Do not freestyle extension point registration in
`plugin.xml` — every EP name must be copied from a verified source.

## Threading and read/write actions — the rules that prevent freezes

These are hard constraints of the platform. Violating them causes editor freezes,
exceptions in the IDE log, or flaky behavior that "works on my run":

- All PSI access requires a **read action**. Code called by the platform (e.g.
  `LineMarkerProvider.getLineMarkerInfo`) is already inside one — do not start threads
  or coroutines from there that touch PSI without their own read action.
- Never do slow work (deep call-graph walks on cold cache) directly in
  `getLineMarkerInfo` for the "fast pass". Use the platform's intended split:
  cheap/collected markers in the fast path, expensive analysis in
  `collectSlowLineMarkers` / cached computation.
- Never block the EDT (UI thread). No analysis on EDT, ever.
- Respect **dumb mode**: if `DumbService.isDumb(project)`, return no markers rather
  than throwing `IndexNotReadyException`. Wrap index access accordingly.
- Cancellation: platform analysis is cancelled and restarted constantly. Long walks
  must call `ProgressManager.checkCanceled()` (or run under a progress indicator) so
  they abort promptly. Swallowing `ProcessCanceledException` is a bug — always rethrow.

## Caching — the part most likely to go subtly wrong

- Per-method coloring results are cached via `CachedValuesManager` keyed on the method,
  with dependency on **PSI modification tracking** (out-of-code-block modification
  tracker at minimum). Do not hand-roll a HashMap cache keyed on PSI elements —
  PSI elements are not stable keys across reparses.
- When markers seem stale in manual testing, the bug is almost always a missing or
  wrong cache dependency — investigate that before adding invalidation hacks like
  `DaemonCodeAnalyzer.restart()` calls sprinkled around.
- The transitive walk must be depth-limited (config value) and cycle-safe
  (visited set per walk).

## Line marker correctness details

- Return `LineMarkerInfo` anchored on a **leaf element** (the method's name
  identifier), not on the whole `PsiMethod`. Anchoring on large elements causes
  icon flicker. This is documented platform guidance — follow it.
- One provider, cheap checks first: bail out fast on elements that can't be method
  name identifiers before doing anything else.

## Spring-specific resolution rules

- A call to a method on an interface extending Spring Data `Repository` is a DB sink
  axiomatically — there is no implementation to resolve into. Detect via
  supertype check, not name matching.
- For calls through interfaces (DI), resolve to implementations via the platform's
  inheritance search; if multiple implementations exist, a method is colored if ANY
  implementation is colored (conservative-OR), and the tooltip should say which.
- `@Cacheable` on the resolved method ⇒ category "conditional", do not walk deeper
  for that edge.
- JetBrains `@Blocking` ⇒ sink; `@NonBlocking` ⇒ stop propagation through that method.

## Architecture constraints

- Two modules: `core` (sink taxonomy, YAML config parsing, coloring model, pure Java,
  ZERO `com.intellij.*` imports — enforce with a test or ArchUnit rule) and `plugin`
  (everything PSI/IDE). Do not let platform types leak into `core` signatures.
- Sink definitions and matching logic live in `core` and are unit-tested without the
  IDE. The `plugin` module translates PSI facts (qualified names, annotations,
  supertypes) into `core` queries.

## Verification loop — how you prove your work

- `./gradlew runIde` starts a sandbox IDE with the plugin installed. A feature is not
  done until it has been observed working in the sandbox on the test project.
- Keep a small **fixture Spring Boot project** in `test-fixtures/` containing one
  example of every sink category and every tricky case (repository call, RestClient,
  interface+two-impls, @Cacheable, deep chain exceeding depth limit, cycle). Manual
  sandbox testing opens this project.
- Platform tests: use the IntelliJ test framework (`LightJavaCodeInsightFixtureTestCase`
  style) for marker presence/absence on fixture code. Verify the current recommended
  test base class against the reference repos first — this API area churns too.
- After any change to threading/caching code, test in the sandbox with an actual
  edit-undo-edit sequence on a colored method to confirm invalidation works.
- Check `idea.log` in the sandbox for exceptions after manual testing. A run with
  stack traces in the log is a failed run even if icons looked right.

## Known traps (learned the hard way, do not rediscover)

- Mixing gradle plugin 1.x and 2.x DSL snippets → build fails with confusing errors.
- Registering an extension point with a typo'd or removed EP name → plugin silently
  does nothing. Copy EP names character-for-character from verified sources.
- Doing `resolve()` on every PsiElement in the file in the fast pass → editor lag.
  Filter by element type first (cheap), resolve only plausible candidates.
- Catching broad `Exception` around platform calls and continuing → swallows
  `ProcessCanceledException` and corrupts platform assumptions. Catch narrowly.
- "It compiles" means nothing here. Half the failure modes (threading, dumb mode,
  stale cache) only appear at runtime in the sandbox.

## Scope discipline

- SPEC.md defines the product. Do not add IDE settings UI, quick-fixes, inspections,
  or non-Java language support unless SPEC.md changes first.
- Prefer boring platform-blessed mechanisms over clever ones. If a solution requires
  an undocumented API or reflection into platform internals, stop and flag it.