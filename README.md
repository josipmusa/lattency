# <img src="docs/images/lattency.svg" alt="Lattency icon" width="40"> Lattency

**An IntelliJ IDEA plugin that shows you where your Java code leaves the process.**

Reading a pull request, the question you keep asking is *does this change add I/O, or
move it somewhere worse?* Answering it means clicking down the call chain until you hit
a repository or an HTTP client. Lattency puts the answer in the gutter.

<img src="docs/images/markers-dark.png#gh-dark-mode-only" alt="Lattency gutter markers on a Spring service, dark theme" width="820">
<img src="docs/images/markers-light.png#gh-light-mode-only" alt="Lattency gutter markers on a Spring service, light theme" width="820">

Reading that gutter: `place` carries the neutral icon because it reaches three different
kinds of I/O, and each of those lines carries its own category icon - a database write, an
HTTP call, a message publish. `normalize`, just below it, is pure and carries nothing.
`recentFor` queries the database directly. Inside `exportInvoice`, three lines are file
I/O: a call into a project type declared as a file sink in `lattency.yml`, opening a
`FileOutputStream`, and writing to it.

Hovering a marker gives the chain that earned it, and clicking navigates to any method
along that chain:

<img src="docs/images/tooltip-dark.png#gh-dark-mode-only" alt="Tooltip listing the database, HTTP and messaging chains reached from place(), dark theme" width="820">
<img src="docs/images/tooltip-light.png#gh-light-mode-only" alt="Tooltip listing the database, HTTP and messaging chains reached from place(), light theme" width="820">

## What it marks

A method is marked when its body performs I/O, or when it calls something that does, up
to a configurable depth. A method that only *reaches* I/O gets a dimmed variant of the
category icon, so it stays distinguishable from one that performs the I/O itself. A method
that reaches more than one category gets the neutral icon instead.

Nothing needs configuring on a normal Spring Boot project:

| | |
|---|---|
| **Database** | Spring Data repositories (by supertype, not by name), JDBC, `DataSource`, `JdbcTemplate`, `JdbcClient` |
| **HTTP** | `RestClient`, `RestTemplate`, `WebClient`, `java.net.http.HttpClient`, OkHttp, Feign |
| **Messaging** | Kafka producers, Google Cloud Pub/Sub, `JmsTemplate`, `RabbitTemplate` |
| **File** | `java.nio.file.Files`, `java.io` streams and readers, and opening one of them |
| **Generic** | Anything annotated `@Blocking` |

It also resolves calls through interfaces to their project implementations, and
understands three annotations:

| Annotation | Effect |
|---|---|
| `@Blocking` (JetBrains) | The method is a sink, category generic |
| `@NonBlocking` (JetBrains) | The method is never marked, and propagation stops there |
| `@Cacheable` (Spring), `@CacheResult` (JSR-107) | Calls *into* the method are conditional: the walk stops rather than claiming the I/O underneath, because on a cache hit the body never runs. The annotated method itself is still marked normally |

The analysis is entirely static. No agent, no running application, no network, no
telemetry.

## Install

Download the ZIP from [Releases](https://github.com/josipmusa/lattency/releases), then
*Settings | Plugins | ⚙ | Install Plugin from Disk*.

Requires IntelliJ IDEA 2025.2 or newer, and Java code. Kotlin is not supported.

Not on the JetBrains Marketplace yet.

## Configuration

Optional. A `lattency.yml` at your project root teaches Lattency about code it cannot
recognise on its own - your own HTTP wrapper, your in-house storage client - and quiets
down the parts of the codebase you do not want marked.

Commit it; it applies to everyone on the project and takes effect without restarting the
IDE. Nothing outside the project is read, and no setting is stored per user.

```yaml
# every key is optional
depth: 4

sinks:
  - match:
      class: com.example.infra.BlobStore
    category: FILE

exclude:
  - com.example.legacy
```

### `depth`

How many project-method hops to follow from a method before giving up. Default `4`,
maximum `10`. A method that calls a repository directly is depth 0.

A chain longer than `depth` leaves the method **unmarked**, rather than marked with a
truncated chain. A bounded walk cannot tell "nothing down there" from "something just
past the horizon", so a capped-but-still-marked result would depend on which methods
happened to be analysed first. Unmarked is the answer that is the same every time.

### `sinks`

Extra rules, applied on top of the built-in ones. `category` is one of `DB`, `HTTP`,
`MESSAGING`, `FILE`, `GENERIC`, and selects the icon and the tooltip label. `match` takes
exactly one of five shapes:

```yaml
sinks:
  # Every method on every type in this package and its subpackages.
  - match:
      package: com.example.storage
    category: FILE

  # Every method on exactly this type.
  - match:
      class: com.example.infra.BlobStore
    category: FILE

  # One method on one type.
  - match:
      class: com.example.infra.Registry
      method: lookup
    category: HTTP

  # Every method annotated with this, and every method of a type annotated with it.
  - match:
      annotation: com.example.infra.RemoteCall
    category: HTTP

  # `new Connection(..)` - for types where constructing one IS the I/O.
  - match:
      construction: com.example.infra.Connection
    category: DB
```

`package`, `class`, `class` + `method` and `annotation` describe an **API surface**:
calling into the type is I/O. Constructing the type is not calling it. That separation is
what lets Lattency mark `new FileOutputStream(path)` while leaving `new File(path)` alone
even though both are `java.io` types it knows about - and it means a `package:` rule on
your Kafka wrapper will not fire on `new ProducerRecord<>(..)`. If constructing one of
your types really is the I/O, say so with `construction`.

`annotation` is the exception, and applies to constructors too, because an annotation
names the exact declaration it sits on.

### `exclude`

Package prefixes or fully-qualified class names to leave out of the analysis. Matching is
by prefix on package boundaries, so `com.example.legacy` excludes
`com.example.legacy.Store` but not `com.example.legacyish.Store`.

**Exclusion is stronger than "do not draw an icon".** Excluded code disappears from the
analysis entirely, so it is not marked *and* it stops contributing I/O to its callers.
Use it for code you have decided not to reason about - generated sources, a legacy corner
you are not touching - and not merely to reduce icons. Exclusions win over every sink
rule, built-in or your own.

Test sources are analysed like any other code. Exclude their packages if you do not want
markers there.

### When the file is wrong

A malformed `lattency.yml`, an unknown `category`, or a `match` that is not one of the
five shapes makes Lattency fall back to the built-in rules alone and log a warning to
`idea.log` (**Help | Show Log in Finder/Explorer**). It never fails the IDE and never
partially applies a broken file. There is no in-editor error for this yet.

Editing the file inside the IDE re-analyses open files immediately. An external edit
takes effect once the IDE notices it on disk, which it does when you bring the window
back to the front.

## Turning it off

*Settings | Editor | General | Gutter Icons* → uncheck **Lattency I/O**. That stops the
analysis, not just the drawing.

## Limitations

Lattency prefers a missed marker to a wrong one, and most of what follows is a missed
marker.

**Not detected.** Method references (`orders.forEach(repository::save)`; the same code as
a lambda is detected). Anything reached by reflection, a proxy, or a service loader. Lazy
JPA association loading. Library internals, which are described by sink rules covering an
API surface rather than by walking bytecode - if a library does I/O behind a method with
no rule for it, the trail ends there. Field and static initialisers get a call-site marker
but have no enclosing method to mark. Kotlin.

**Marked when you might not want it.** A sink inside a lambda colours the enclosing
method, even when the lambda is stored and run later rather than inline. Lattency cannot
tell the two apart, and marking is the more useful default.

**Less precise than the icon.** When a call goes through an interface with several
coloured implementations, the marker on the declaration names whichever gives the
shortest chain, while the marker on the call line lists them all; the category is correct
in both. Navigation targets are resolved by name, so the click-through popup can also
offer overloads that are not the one the chain used.

**Cross-file staleness.** Editing a method so it no longer performs I/O updates its
callers in the same file immediately. Callers in *other* files update when those files
are next re-highlighted. This matches how the platform's own "is overridden" markers
behave.

## Build from source

```shell
./gradlew build          # compile, unit tests, ArchUnit rule, IntelliJ platform tests
./gradlew runIde         # sandbox IDE with the plugin installed
./gradlew buildPlugin    # installable ZIP in intellij-adapter/build/distributions/
```

Two modules: `core` holds the sink taxonomy, config parsing and coloring model in plain
Java with zero `com.intellij.*` imports (enforced by a test); `intellij-adapter` holds
everything that touches PSI and the editor.

`test-fixtures/` is a small standalone project with one named class per supported,
suppressed and excluded case - open it in the sandbox to see every marker at once:

```shell
./gradlew runIde --args="$(pwd)/test-fixtures"
```

See [CONTRIBUTING.md](CONTRIBUTING.md) to work on it.

## License

[MIT](LICENSE).
