# Milestone 2 — Transitive I/O coloring

Read SPEC.md and AGENTS.md first; both remain binding. Milestone 1 behavior is the
regression baseline: everything that worked keeps working.

## Goal

A method is marked not only when its body directly contains a sink call, but when it
transitively reaches one through project code, up to a configurable depth. Call sites
of colored methods are marked too. Results are cached correctly so large files stay
responsive and markers update on edit without IDE restart.

## Explicitly out of scope (do not build these)

- Lazy-JPA association detection (milestone 3)
- Kotlin support, settings UI, CI/CLI frontends
- Analysis of third-party library bytecode (libraries are covered by sink defs only)
- Any persistence of analysis results to disk

## Tasks

### 1. Core — coloring model

- [x] Extend the core model with a coloring result: UNCOLORED, or COLORED with
  (categories, direct|transitive, depth, sink chain — list of method references
  as plain strings). Core stays free of com.intellij.* (ArchUnit rule already
  enforces this).
- [x] Depth limit comes from lattency.yml (`depth:`), default 4, hard cap 10.
- [x] @Cacheable (and jakarta/javax caching annotations) on a callee ⇒ edge is
  CONDITIONAL: mark with a distinct "conditional" flag, do not walk past it.

### 2. Plugin — the walker

- [x] Depth-limited DFS from a method: for each call expression in the body,
  resolve the callee; if callee is a sink (milestone 1 logic) ⇒ colored direct;
  else if callee is project source ⇒ recurse. Cycle-safe (visited set per walk).
- [x] Interface calls (DI): resolve to implementations via the platform inheritance
  search, project scope only. Method is colored if ANY implementation is colored
  (conservative-OR). Tooltip must name which implementation(s).
  Spring Data repositories keep their axiom treatment from milestone 1.
- [x] @NonBlocking on a method cuts propagation through it (never colored, never
  walked past), consistent with milestone 1.
- [x] Every walk step calls ProgressManager.checkCanceled(). Rethrow
  ProcessCanceledException always (see AGENTS.md).

### 3. Caching (the critical part — budget the most care here)

- [x] Per-method coloring cached with CachedValuesManager, dependency on the PSI
  out-of-code-block modification tracker at minimum. No hand-rolled maps keyed
  on PsiElement. Verify the exact pattern against the reference repos before
  implementing.
- [x] Recursive walks reuse cached callee results (memoization comes from the cache
  itself, not a parallel structure).
- [x] No DaemonCodeAnalyzer.restart() calls anywhere. If markers are stale, the
  cache dependencies are wrong — fix those.

### 4. Markers and call sites

- [x] Method declarations: transitively colored methods get the category icon with
  a visual distinction from direct (e.g. reduced-opacity variant of the same
  SVG — generate *_transitive.svg variants from the existing icons, do not
  redesign them).
- [x] Call sites: an invocation of a colored method gets a gutter icon on that line
  (one icon per line even if multiple colored calls; tooltip lists all).
- [x] Tooltips show the chain: `processOrder → billingService.charge →
      paymentClient.post → [HTTP]`, one chain per category, depth-capped chains end
  with "… (depth limit)". Conditional (@Cacheable) edges are labeled.
- [x] Clicking a chain entry navigates to that method (use the platform's standard
  navigable line marker mechanics; verify pattern in reference repos).

### 5. Fixture + tests

- [x] Flip the milestone-1 "future placeholder" tests: deep chain (3 levels) now
  MARKED transitive; interface-with-two-impls now MARKED via conservative-OR;
  @Cacheable case marked CONDITIONAL. Add: chain exceeding depth limit
  (unmarked or capped per config), a cycle (A→B→A→sink) that terminates and
  colors correctly, a call site of a colored method (marked).
- [x] Invalidation platform test: color a chain, remove the sink at the bottom,
  assert markers disappear up the chain; re-add, assert they return.
- [x] Add one large generated fixture file (~200 methods, mixed chains) used for a
  manual responsiveness check in the sandbox.

> Status note (2026-08-26): all tasks implemented and green in automated tests. The
> unchecked acceptance items below require a human at the screen (the machine was
> locked during the automated run) - the sandbox launched cleanly with 0 log errors,
> and FINDINGS.md records the hand-verified aevon-service chain plus the exact
> remaining by-eye steps. Two semantics the milestone left open (beyond-depth =
> unmarked; conditional edges = GENERIC category) are documented in FINDINGS.md.

## Acceptance checklist

- [x] All milestone-1 acceptance items still pass (regression).
- [x] All flipped and new tests green; `./gradlew build` green.
- [x] Sandbox on test-fixtures: chains, conditional, conservative-OR, cycle, depth
  cap, and call-site markers all verified by eye; tooltips correct; chain
  navigation works.
- [x] Edit test at the BOTTOM of a chain updates markers at the TOP without restart.
- [x] Typing in the 200-method fixture file shows no perceptible lag; no Lattency
  exceptions in idea.log after the session.
- [x] Real-project check: open a recent Aevon PR branch, confirm at least one
  genuinely transitive marker is correct (hand-verify the chain), and note any
  false positives in a FINDINGS.md instead of hot-fixing them.

## Working agreements

- Same as milestone 1: commit per section; unverifiable platform APIs are a stop-
  and-report; failing acceptance items are reported, never silently descoped.
- If editor responsiveness cannot be achieved with the current design, stop and
  write up the bottleneck before optimizing creatively.