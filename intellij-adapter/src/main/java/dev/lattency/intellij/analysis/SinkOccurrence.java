package dev.lattency.intellij.analysis;

import dev.lattency.core.IoCategory;

public record SinkOccurrence(String callee, IoCategory category) {}
