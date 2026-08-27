# Contributing

Bug reports and pull requests are welcome. This file covers what you need to work on the
plugin. [AGENTS.md](AGENTS.md) records the platform behaviour that shaped the analysis
and the caching, and is worth reading before changing either.

## Setup

You need JDK 21 and nothing else - Gradle downloads the IntelliJ Platform on first build.

```shell
git clone https://github.com/josipmusa/lattency
cd lattency
./gradlew build
```

Open the repository root as a Gradle project in IntelliJ IDEA.

## The loop

```shell
./gradlew build                                  # everything CI runs, minus the verifier
./gradlew runIde --args="$(pwd)/test-fixtures"   # sandbox IDE on the fixture project
./gradlew verifyPlugin                           # binary compatibility across IDE builds
```

`runIde` launches a second IDE with the plugin installed. **A change is not done until
you have watched it work there.** Compiling proves very little in this codebase: dumb
mode, stale caches and threading mistakes only show up at runtime.

After a sandbox session, check its log for anything Lattency threw:

```shell
grep " ERROR " .intellijPlatform/sandbox/*/*/log/idea.log
```

A run with a Lattency stack trace in the log is a failed run even if the icons looked
right.

## Tests

| | |
|---|---|
| `core/src/test` | Plain JUnit 5. Sink matching, YAML parsing, the coloring model, and an ArchUnit rule asserting `core` imports nothing from `com.intellij.*`. Fast; no IDE. |
| `intellij-adapter/src/test` | `LightJavaCodeInsightFixtureTestCase`. Marker presence and absence on real Java source, invalidation after an edit, and a bound on how long a wide fan-out may take to colour. |

Marker behaviour belongs in the platform tests - assert on the gutter, not on internals.
If you are adding a sink category or a matching rule, it needs a case in both layers: the
rule in `core`, the end-to-end marker in `intellij-adapter`.

Two of the platform tests exist to pin performance characteristics rather than
correctness (`testWideFanOutStaysFast`). If one starts failing, the analysis has changed
shape; do not raise the bound without understanding why.

## Working on the analysis

The platform rules that matter here, in short:

- **All PSI access needs a read action.** Code the platform calls you from already has
  one; do not start threads that touch PSI without their own.
- **Never block the EDT**, and keep filesystem access off the highlighting path entirely.
- **Respect dumb mode.** During indexing, return no markers rather than throwing
  `IndexNotReadyException`.
- **Call `ProgressManager.checkCanceled()` in every loop of a walk.** Analysis is
  cancelled and restarted constantly. Never swallow `ProcessCanceledException` - catching
  broad `Exception` around platform calls does exactly that, and corrupts the platform's
  assumptions.
- **Cache with `CachedValuesManager`, keyed on the method, with a PSI modification
  tracker dependency.** Do not hand-roll a map keyed on `PsiElement`; PSI elements are
  not stable keys across reparses.
- **Anchor markers on leaf elements** - a name identifier, not a whole `PsiMethod` or
  reference. The platform asserts on this in tests.
- If markers go stale, a cache dependency is wrong. Fix that. Do not add
  `DaemonCodeAnalyzer.restart()` calls.

Do not call a `com.intellij.*` API you have not confirmed exists in the platform version
this project targets. The SDK churns between releases and plausible-sounding classes
often do not exist. Check the resolved external libraries in the IDE, or the
[intellij-community](https://github.com/JetBrains/intellij-community) source. Deprecation
warnings are errors here: find the replacement rather than suppressing it.

## Scope

Lattency is a lens, not a linter. Additions that would change that - quick fixes,
inspections, an IDE settings panel, runtime measurement - are out of scope. Sink rules
for libraries in wide use, and precision improvements to the walk, are very much in
scope.

New detection should be conservative. A false positive teaches reviewers to ignore the
gutter, which costs more than the marker was worth.

## Pull requests

- One concern per PR, with the sandbox observation described in the body.
- Add a line to the `[Unreleased]` section of [CHANGELOG.md](CHANGELOG.md).
- CI runs the test suite and the Plugin Verifier; both must be green.§
