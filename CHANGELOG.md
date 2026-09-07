<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Lattency Changelog

## [Unreleased]

### Added

- `ignore` section in `lattency.yml`: `ignore.categories` turns off whole I/O categories
  (for example `[DB]`), and `ignore.sinks` turns off individual APIs in the same five
  match shapes as `sinks` while keeping the rest of their category marked.

- A warning balloon when `lattency.yml` cannot be applied, with the same text as the
  log line.

### Fixed

- Editing `lattency.yml` in the IDE now applies about a second after you stop typing,
  and re-highlights open files, instead of waiting for autosave and for each file to be
  touched.
- An unknown `category` in `lattency.yml` now logs the valid names alongside the
  rejected one.

## [0.1.0]

First public release.

### Added

- Gutter markers on every Java method that performs I/O, directly or transitively,
  with a distinct icon per category: database, HTTP, messaging, file, and generic.
- Zero-configuration sink rules for Spring Data repositories, `RestClient`,
  `RestTemplate`, `WebClient`, `HttpClient`, OkHttp, Feign, JDBC, Kafka, Pub/Sub,
  JMS, RabbitMQ, and `java.io` / `java.nio.file`.
- Transitive coloring through project code up to a configurable depth, cycle-safe and
  cancellable, with a reduced-opacity icon variant distinguishing transitive from direct.
- Call-site markers: one gutter icon per line that invokes I/O-colored code.
- Tooltips that name the whole chain, e.g. `place → OrderRepository.save → [DB]`, and
  a click-through popup that navigates to any method along it.
- `lattency.yml` at the project root: custom sinks by package, class, class + method,
  annotation, or construction; exclusions; and the propagation depth. Changes apply
  without restarting the IDE.
- `@Blocking` counts as a sink and `@NonBlocking` cuts propagation; `@Cacheable` and
  `@CacheResult` make the edge into a method conditional rather than unconditional I/O.
- Conservative-OR resolution through interfaces: a call is colored when any project
  implementation is colored, and the tooltip names the implementation.
- Markers can be turned off in *Settings | Editor | General | Gutter Icons*.

[Unreleased]: https://github.com/josipmusa/lattency/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/josipmusa/lattency/releases/tag/v0.1.0
