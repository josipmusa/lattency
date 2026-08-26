package dev.lattency.fixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.NonBlocking;

public final class NonBlockingSuppressionCase {
    @NonBlocking
    public String intentionallySuppressed(Path path) throws IOException {
        return Files.readString(path);
    }
}
