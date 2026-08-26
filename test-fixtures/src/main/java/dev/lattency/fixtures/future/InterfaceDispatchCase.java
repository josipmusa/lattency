package dev.lattency.fixtures.future;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class InterfaceDispatchCase {
    // lattency-future: invoke should be colored only when implementation resolution is added.
    public String invoke(ContentSource source, Path path) throws IOException {
        return source.load(path);
    }

    public interface ContentSource {
        String load(Path path) throws IOException;
    }

    public static final class FileContentSource implements ContentSource {
        @Override
        public String load(Path path) throws IOException {
            return Files.readString(path);
        }
    }

    public static final class MemoryContentSource implements ContentSource {
        @Override
        public String load(Path path) {
            return "memory";
        }
    }
}
