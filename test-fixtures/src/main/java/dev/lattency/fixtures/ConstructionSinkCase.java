package dev.lattency.fixtures;

import dev.lattency.fixtures.support.CustomRemoteClient;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Construction is a sink only for types where opening one IS the I/O. */
public final class ConstructionSinkCase {

    // Expect: FILE marker, direct - opening the stream touches the filesystem.
    public void write(String name, byte[] bytes) throws IOException {
        new FileOutputStream(name).write(bytes);
    }

    // Expect: no marker - naming a file touches nothing, and the custom sink in
    // lattency.yml matches calls to CustomRemoteClient, not its construction.
    public File name(String path) {
        new CustomRemoteClient();
        return new File(path);
    }

    // Expect: FILE marker, transitive - through this class's own constructor.
    public ConstructionSinkCase copy(String name, byte[] bytes) throws IOException {
        return new ConstructionSinkCase(name, bytes);
    }

    // Expect: FILE marker, direct.
    public ConstructionSinkCase(String name, byte[] bytes) throws IOException {
        new FileOutputStream(name).write(bytes);
    }

    public ConstructionSinkCase() {}
}
