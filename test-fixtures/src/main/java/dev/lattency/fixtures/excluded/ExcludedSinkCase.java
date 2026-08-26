package dev.lattency.fixtures.excluded;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ExcludedSinkCase {
    public String read(Path path) throws IOException {
        return Files.readString(path);
    }
}
