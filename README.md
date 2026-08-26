# Lattency

Lattency is an IntelliJ IDEA plugin that will mark direct and transitive I/O in Java code.
The current scaffold proves the end-to-end gutter integration by marking every Java method
named `test`.

## Modules

- `core`: IntelliJ-independent I/O taxonomy and coloring model.
- `intellij-adapter`: IntelliJ PSI and presentation integration.

## Run

```shell
./gradlew test
./gradlew :intellij-adapter:runIde --args="$(pwd)/demo/src/main/java/Demo.java"
```

The sandbox IDE opens `Demo.java`. A purple arrow appears in the gutter beside `test`, while
`unmarked` has no Lattency marker.
