# AGENTS.md - guidance for AI coding agents on this project

This project is an IntelliJ Platform plugin. This is a domain where coding agents
reliably hallucinate APIs. Read this file fully before writing code. When this file
conflicts with your instinct, this file wins.

Human contributors want [CONTRIBUTING.md](CONTRIBUTING.md) instead; it covers the same
build and verification loop without the API-hallucination warnings.

## What the product is

An IntelliJ IDEA plugin that visually marks code performing I/O - network, database,
messaging, file system - directly or transitively, so a reviewer can see at a glance
where a method "leaves the process" without clicking through the call chain.

The user is a developer reviewing PRs with the branch open in IDEA, on a Java / Spring
Boot codebase. The product is passive and ambient: no actions to invoke, no windows to
open. The signal lives in the editor gutter and on call sites.

Properties the product must keep:

- **Static only.** No running application, no agent, no runtime data, no network.
- **Categories are visible.** DB, HTTP, messaging, file and generic I/O are
  distinguishable, and a method colored only transitively is distinguishable from one
  that performs I/O itself.
- **The marker explains itself.** The tooltip names the sink chain, and clicking
  navigates to any method along it.
- **Zero config is useful.** Built-in rules cover the common Java/Spring stack; a
  committed `lattency.yml` extends them. Config changes apply without an IDE restart.
- **False negatives are acceptable; noisy false positives are the bigger sin.** When in
  doubt, mark less with a clear "why" rather than more. A false positive teaches
  reviewers to ignore the gutter, which costs more than the marker was worth.
- **The analysis stops at async boundaries.** Publishing a message is the sink; whatever
  consumes it is out of scope.
- **Library internals are not analysed.** Third-party code is covered by sink definitions
  describing its API surface, never by walking its bytecode.
- **Marking never noticeably degrades editor responsiveness**, and the plugin behaves
  correctly during indexing: show nothing rather than wrong things, and recover.

The behaviours users depend on - the `lattency.yml` format and the known limitations -
are documented in [README.md](README.md). Changing one of them means changing the README
in the same commit.

## The #1 rule: never invent IntelliJ Platform APIs

The IntelliJ SDK churns between releases. Your training data mixes API eras
(2019–2024) and contains plausible-sounding classes and extension points that do not
exist or are deprecated. Therefore:

- **Never call a platform API from memory alone.** Before using any class/method from
  `com.intellij.*`, verify it exists in THIS project's resolved SDK: check the external
  libraries the Gradle plugin downloaded, or grep the reference repos (below).
- If you cannot verify an API, say so and look it up - do not guess a similar name.
- Deprecation warnings are errors for us: find the current replacement, don't suppress.

## Pinned versions - do not drift

- Target platform: the build named by `platformVersion` in `gradle.properties`
  (IntelliJ IDEA 2025.2.x). Do not change platform/plugin versions to "fix" a compile
  error without explaining why.
- Build system: **intellij-platform-gradle-plugin (2.x)**. Do NOT use the legacy
  `org.jetbrains.intellij` (1.x) plugin, and do not mix docs/snippets between the two -
  their DSLs differ and 1.x examples will not work.
- `sinceBuild` is pinned in `gradle.properties`; `untilBuild` is deliberately open and
  kept honest by `verifyPlugin` in CI. Do not pin `untilBuild` to silence a verifier
  failure - fix the incompatibility, or raise it as a decision.
- Language: Java for production code (Kotlin allowed only if a platform API forces it;
  isolate it). JDK version: the `javaVersion` property in `gradle.properties`.

## Reference repos are the source of truth for patterns

Clone-if-missing into `.references/` (gitignored) and grep them before implementing any
platform integration point:

- `JetBrains/intellij-sdk-code-samples` - canonical minimal examples
  (line markers, inspections, services, settings).
- `JetBrains/intellij-community` - the platform source itself; the ultimate answer to
  "what does this API actually do" and "how does the platform's own code use it".
- `JetBrains/intellij-platform-plugin-template` - the reference for build, CI, signing
  and changelog wiring.
- `digma-ai/digma-intellij-plugin` - a real production plugin doing editor markers,
  caching, and background analysis.

Pattern to follow: find how the sample/production code registers and implements the
thing, copy that shape, adapt. Do not freestyle extension point registration in
`plugin.xml` - every EP name must be copied from a verified source.

## Threading and read/write actions - the rules that prevent freezes

These are hard constraints of the platform. Violating them causes editor freezes,
exceptions in the IDE log, or flaky behavior that "works on my run":

- All PSI access requires a **read action**. Code called by the platform (e.g.
  `LineMarkerProvider.getLineMarkerInfo`) is already inside one - do not start threads
  or coroutines from there that touch PSI without their own read action.
- Never do slow work (deep call-graph walks on cold cache) directly in
  `getLineMarkerInfo` for the "fast pass". Use the platform's intended split:
  cheap/collected markers in the fast path, expensive analysis in
  `collectSlowLineMarkers` / cached computation.
- Never block the EDT (UI thread). No analysis on EDT, ever. This includes filesystem
  access: cached-value dependencies are validated on every access, so a `stat` in a
  `ModificationTracker` lands on the highlighting path thousands of times per keystroke.
  Drive trackers from VFS events instead.
- Respect **dumb mode**: if `DumbService.isDumb(project)`, return no markers rather
  than throwing `IndexNotReadyException`. Wrap index access accordingly.
- Cancellation: platform analysis is cancelled and restarted constantly. Long walks
  must call `ProgressManager.checkCanceled()` (or run under a progress indicator) so
  they abort promptly. Swallowing `ProcessCanceledException` is a bug - always rethrow.

## Caching - the part most likely to go subtly wrong

- Per-method coloring results are cached via `CachedValuesManager` keyed on the method,
  with dependency on **PSI modification tracking**. Do not hand-roll a HashMap cache
  keyed on PSI elements - PSI elements are not stable keys across reparses.
- When markers seem stale in manual testing, the bug is almost always a missing or
  wrong cache dependency - investigate that before adding invalidation hacks like
  `DaemonCodeAnalyzer.restart()` calls sprinkled around.
- The transitive walk must be depth-limited (config value) and cycle-safe
  (per-walk path set).
- **The walk also needs a memo, and the memo is not the path set.** The path set is
  popped on the way back up; without a separate memo, a call graph that fans out and
  re-converges re-derives shared subtrees once per path, which is exponential in the
  depth limit. Measured on a synthetic fan-out, colouring a single method: 12 ms at
  8 wide x 3 deep, 130 ms at 8 x 4, 607 ms at 12 x 4. With the memo: 8 ms, 8 ms, 10 ms.
  Two rules keep the memo correct: a result is reused for any budget *at or below* the
  one it was computed with, and a result is memoised only if **no cycle was cut while
  computing it** (a result that depended on a cycle being cut depended on which methods
  were on the path at that moment).
- **Callee results are reused from the platform cache by peeking, never by computing a
  nested cached value.** Recursive `CachedValuesManager.getCachedValue` calls between
  methods trip `RecursionManager`'s caching prevention on cyclic code - the test
  framework asserts on it, and production silently stops caching. Each cached value
  comes from one self-contained, budget-bounded walk that reuses other methods' caches
  only via `CachedValue.getUpToDateOrNull()`.
- **Depend on `PsiModificationTracker.MODIFICATION_COUNT`.** It bumps on method-body
  edits, which transitive colouring needs. Do not reach for the out-of-code-block
  `JAVA_STRUCTURE_MODIFICATION_COUNT`: in 2025.2 it is literally a deprecated alias of
  `MODIFICATION_COUNT`, not a narrower tracker.
- **Cache invalidation alone does not refresh markers.** Java's own
  `ChangeLocalityDetector` shrinks the re-highlighting scope of an in-body edit to the
  enclosing code block, so markers on *callers* elsewhere in the file are never
  requeried and go stale. Lattency registers its own detector (`order="first"`) widening
  a method-body change to the containing file, and skips it entirely when the user has
  unchecked **Lattency I/O** in Gutter Icons settings - no markers, no widening, no cost.

## Line marker correctness details

- Return `LineMarkerInfo` anchored on a **leaf element** (a name identifier, a
  reference's name element), not on the whole `PsiMethod` or a whole reference.
  Anchoring on large elements causes icon flicker, and the platform asserts on it in
  tests.
- One provider, cheap checks first: bail out fast on elements that can't be method
  name identifiers before doing anything else.

## Spring-specific resolution rules

- A call to a method on an interface extending Spring Data `Repository` is a DB sink
  axiomatically - there is no implementation to resolve into. Detect via
  supertype check, not name matching.
- For calls through interfaces (DI), resolve to implementations via the platform's
  inheritance search; if multiple implementations exist, a method is colored if ANY
  implementation is colored (conservative-OR), and the tooltip should say which.
- `@Cacheable` on the resolved method ⇒ the edge into it is "conditional", do not walk
  deeper for that edge.
- JetBrains `@Blocking` ⇒ sink; `@NonBlocking` ⇒ stop propagation through that method.

## Sink matching rules

- Type-shaped rules (package, class, class + method, supertype) describe an **API
  surface**: they match calls, never construction. Construction has its own rule kind.
  This is what keeps `new File(name)` and `new ProducerRecord<>(..)` unmarked while
  `java.io.File` and the Kafka producer package remain sinks. Annotation rules are the
  exception and apply to constructors too.
- Do not add a sink definition you have not confirmed actually performs I/O at the point
  it is matched.

## Architecture constraints

- Two modules: `core` (sink taxonomy, YAML config parsing, coloring model, pure Java,
  ZERO `com.intellij.*` imports - enforced by an ArchUnit test) and `intellij-adapter`
  (everything PSI/IDE). Do not let platform types leak into `core` signatures.
- Sink definitions and matching logic live in `core` and are unit-tested without the
  IDE. The adapter translates PSI facts (qualified names, annotations, supertypes)
  into `core` queries.

## Verification loop - how you prove your work

- `./gradlew runIde` starts a sandbox IDE with the plugin installed. A feature is not
  done until it has been observed working in the sandbox on the test project.
- `test-fixtures/` holds one clearly named class per case - repository call, RestClient,
  interface + two impls, `@Cacheable`, a chain past the depth limit, a cycle. Manual
  sandbox testing opens this project:
  `./gradlew runIde --args="$(pwd)/test-fixtures"`.
- Platform tests: `LightJavaCodeInsightFixtureTestCase`, asserting on gutter markers
  rather than internals. Verify the current recommended base class against the reference
  repos first - this API area churns too.
- After any change to threading/caching code, test in the sandbox with an actual
  edit-undo-edit sequence on a colored method to confirm invalidation works.
- Check `idea.log` in the sandbox for exceptions after manual testing. A run with
  stack traces in the log is a failed run even if icons looked right.
- `./gradlew verifyPlugin` before anything that changes plugin.xml, the platform
  version, or an API surface.

## Known traps (learned the hard way, do not rediscover)

- Mixing gradle plugin 1.x and 2.x DSL snippets → build fails with confusing errors.
- Registering an extension point with a typo'd or removed EP name → plugin silently
  does nothing. Copy EP names character-for-character from verified sources.
- Doing `resolve()` on every PsiElement in the file in the fast pass → editor lag.
  Filter by element type first (cheap), resolve only plausible candidates.
- Catching broad `Exception` around platform calls and continuing → swallows
  `ProcessCanceledException` and corrupts platform assumptions. Catch narrowly.
- A Marketplace plugin ID may not contain the word "intellij"; `verifyPluginStructure`
  catches this, and changing an ID after publishing breaks every existing install.
- "It compiles" means nothing here. Half the failure modes (threading, dumb mode,
  stale cache) only appear at runtime in the sandbox.

## Scope discipline

- Lattency is a lens, not a linter. Do not add IDE settings UI, quick-fixes,
  inspections, runtime measurement, or non-Java language support. If you believe the
  product definition at the top of this file should change, raise that as a decision
  rather than implementing past it.
- Prefer boring platform-blessed mechanisms over clever ones. If a solution requires
  an undocumented API or reflection into platform internals, stop and flag it.
- User-visible behaviour changes ship with the doc change in the same commit, and a
  `CHANGELOG.md` entry under `[Unreleased]`.
