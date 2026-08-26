package dev.lattency.intellij;

import com.intellij.openapi.util.IconLoader;
import dev.lattency.core.IoCategory;
import dev.lattency.core.MethodColoring;
import java.util.Set;
import javax.swing.Icon;

public final class LattencyIcons {
    public static final Icon DB = load("/icons/db.svg");
    public static final Icon HTTP = load("/icons/http.svg");
    public static final Icon MESSAGING = load("/icons/messaging.svg");
    public static final Icon FILE = load("/icons/file.svg");
    public static final Icon GENERIC = load("/icons/generic.svg");

    public static final Icon DB_TRANSITIVE = load("/icons/db_transitive.svg");
    public static final Icon HTTP_TRANSITIVE = load("/icons/http_transitive.svg");
    public static final Icon MESSAGING_TRANSITIVE = load("/icons/messaging_transitive.svg");
    public static final Icon FILE_TRANSITIVE = load("/icons/file_transitive.svg");
    public static final Icon GENERIC_TRANSITIVE = load("/icons/generic_transitive.svg");

    private LattencyIcons() {}

    public static Icon forColoring(Set<IoCategory> categories, MethodColoring.Origin origin) {
        boolean transitive = origin == MethodColoring.Origin.TRANSITIVE;
        if (categories.size() != 1) {
            return transitive ? GENERIC_TRANSITIVE : GENERIC;
        }
        return switch (categories.iterator().next()) {
            case DB -> transitive ? DB_TRANSITIVE : DB;
            case HTTP -> transitive ? HTTP_TRANSITIVE : HTTP;
            case MESSAGING -> transitive ? MESSAGING_TRANSITIVE : MESSAGING;
            case FILE -> transitive ? FILE_TRANSITIVE : FILE;
            case GENERIC -> transitive ? GENERIC_TRANSITIVE : GENERIC;
        };
    }

    private static Icon load(String path) {
        return IconLoader.getIcon(path, LattencyIcons.class);
    }
}
