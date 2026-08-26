package dev.lattency.fixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileSinkCase {
    public String read(Path path) throws IOException {
        return Files.readString(path);
    }
}
