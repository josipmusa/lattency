package dev.lattency.core;

/** Categories of process-boundary I/O recognized by Lattency. */
public enum IoCategory {
    DB,
    HTTP,
    MESSAGING,
    FILE,
    GENERIC
}
