package dev.lattency.fixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class InterfaceDispatchCase {
    // Expect: FILE marker, transitive - conservative-OR over the two implementations;
    // the tooltip names FileContentSource (the colored one), not MemoryContentSource.
    public String invoke(ContentSource source, Path path) throws IOException {
        return source.load(path);
    }

    public interface ContentSource {
        String load(Path path) throws IOException;
    }

    public static final class FileContentSource implements ContentSource {
        // Expect: FILE marker, direct.
        @Override
        public String load(Path path) throws IOException {
            return Files.readString(path);
        }
    }

    public static final class MemoryContentSource implements ContentSource {
        // Expect: no marker.
        @Override
        public String load(Path path) {
            return "memory";
        }
    }
}
