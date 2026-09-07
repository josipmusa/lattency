package dev.lattency.fixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** {@code Files.exists} is ignored in lattency.yml; {@code Files.readString} still marks. */
public final class IgnoredSinkCase {
    public boolean present(Path path) {
        return Files.exists(path);
    }

    public String read(Path path) throws IOException {
        return Files.readString(path);
    }
}
