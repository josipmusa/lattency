# FINDINGS - milestone 2

Notes from implementing and verifying transitive I/O coloring. Per the milestone's
working agreement, real-project observations are recorded here instead of hot-fixed.

## Semantic decisions taken where the milestone left a choice

- **Chains beyond the depth limit leave the method unmarked** (the milestone offered
  "unmarked or capped per config"). Unmarked matches SPEC's "up to a configurable
  depth" and keeps coloring deterministic: a "capped but still colored" semantic would
  make marking depend on cache warmth, since a cold bounded walk cannot know that a
  method beyond the limit reaches a sink. Consequence: the "… (depth limit)" tooltip
  suffix from the milestone's tooltip example never occurs - an over-limit chain is
  dropped entirely rather than truncated.
- **Conditional (@Cacheable / @CacheResult) edges carry category GENERIC**, not the
  callee's real category. The walk stops at the caching annotation by design, so
  claiming DB/HTTP under it would assert something the analysis never looked at. The
  tooltip labels the edge `(@Cacheable: conditional)`.
- **The @Cacheable method itself is marked normally** (direct, real category): its
  body always performs the I/O when it runs; the conditionality exists only at call
  sites (Spring proxies intercept external calls, and self-invocation bypasses the
  cache anyway). Only edges INTO it are conditional.
- **@CachePut / @CacheEvict do not make an edge conditional** - they always execute
  the method body.

## Platform findings (learned during implementation, kept for milestone 3+)

- `PsiModificationTracker.MODIFICATION_COUNT` is the current merged tracker; the old
  out-of-code-block `JAVA_STRUCTURE_MODIFICATION_COUNT` is literally its deprecated
  alias in 2025.2. Body edits do bump it, which transitive coloring needs.
- Recursive `CachedValuesManager.getCachedValue` calls between methods trip
  `RecursionManager`'s caching prevention on cyclic code (test framework asserts on
  it; production silently stops caching). The correct shape is the one implemented:
  each cached value comes from a self-contained, budget-bounded walk with a per-walk
  visited set, reusing callee caches only via `CachedValue.getUpToDateOrNull()` peeks.
- Cache invalidation alone does not refresh markers: Java's `ChangeLocalityDetector`
  shrinks the daemon's re-highlighting scope of an in-body edit to the enclosing code
  block, so caller markers elsewhere in the file are never requeried. Lattency
  registers its own detector (order="first") widening method-body changes to the
  containing file. Trade-off: Java in-body edits re-highlight the whole file (the
  platform default for most languages; Java's block-only scope was an optimization).
  **Cross-file staleness remains**: editing a sink out of a method updates callers in
  other files only when those files are re-highlighted - same behavior as the
  platform's own "is overridden" markers.

## Real-project check (aevon-service, branch feature/CU-869ep06m6/journal_schema_posting)

Hand-verified chain (static analysis of the source):

- `JournalService.updateDraft` contains **no direct repository call** - every
  statement calls project methods. Expected marker: **DB, transitive, depth 1**, e.g.
  `updateDraft → JournalService.require → JournalEntryRepository.lockByTenantIdAndClientIdAndId → [DB]`
  (also reachable via `replaceLines` → `JournalLineRepository.saveAll` and
  `flushTranslatingConflict` → `entries.flush`; the shortest chain per category wins).
- `JournalService.post` / `storno` / `deleteDraft` call repositories directly →
  expected **DB, direct**.
- `JournalService.claimNumber` → `sequences.insertIfAbsent` → expected **DB, direct**.

No false positives observed yet; visual confirmation in the sandbox is still pending
(see below), so this section should be revisited after the first by-eye session.

## Verification still pending (needs a human at the screen)

The machine was locked during the automated run, so the sandbox could be launched and
its log checked (0 errors, plugin loaded, fixture project indexed) but markers could
not be observed by eye. Outstanding acceptance items:

1. Sandbox on test-fixtures: chains, conditional, conservative-OR, cycle, depth cap,
   call-site markers, tooltips, chain-entry navigation (all covered by platform tests,
   but the milestone requires eyes on the sandbox).
2. Edit at the bottom of `TransitiveCallChainCase`, watch `topLevel`'s marker update
   without restart (covered by `testRemovingTheSinkAtTheBottomUpdatesMarkersUpTheChain`,
   still needs the manual edit-undo-edit pass).
3. Typing responsiveness in `GeneratedMixedChains` (200 methods), then check
   idea.log for Lattency exceptions.
4. Open `JournalService.java` in aevon-service and confirm the predicted
   `updateDraft` transitive marker above; note any false positives here.
