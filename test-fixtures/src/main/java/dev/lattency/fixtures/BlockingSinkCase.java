package dev.lattency.fixtures;

import org.jetbrains.annotations.Blocking;

public final class BlockingSinkCase {
    @Blocking
    public void waitForExternalWork() {}
}
