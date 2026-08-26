package dev.lattency.intellij;

import com.intellij.openapi.util.IconLoader;
import dev.lattency.core.IoCategory;
import java.util.Set;
import javax.swing.Icon;

public final class LattencyIcons {
    public static final Icon DB = load("db");
    public static final Icon HTTP = load("http");
    public static final Icon MESSAGING = load("messaging");
    public static final Icon FILE = load("file");
    public static final Icon GENERIC = load("generic");

    private LattencyIcons() {}

    public static Icon forCategories(Set<IoCategory> categories) {
        if (categories.size() != 1) {
            return GENERIC;
        }
        return switch (categories.iterator().next()) {
            case DB -> DB;
            case HTTP -> HTTP;
            case MESSAGING -> MESSAGING;
            case FILE -> FILE;
            case GENERIC -> GENERIC;
        };
    }

    private static Icon load(String name) {
        return IconLoader.getIcon("/icons/" + name + ".svg", LattencyIcons.class);
    }
}
