package dev.lattency.intellij;

import com.intellij.openapi.util.IconLoader;
import dev.lattency.core.IoCategory;
import dev.lattency.core.MethodColoring;
import java.util.Set;
import javax.swing.Icon;

public final class LattencyIcons {
    public static final Icon DB = IconLoader.getIcon("/icons/db.svg", LattencyIcons.class);
    public static final Icon HTTP = IconLoader.getIcon("/icons/http.svg", LattencyIcons.class);
    public static final Icon MESSAGING =
            IconLoader.getIcon("/icons/messaging.svg", LattencyIcons.class);
    public static final Icon FILE = IconLoader.getIcon("/icons/file.svg", LattencyIcons.class);
    public static final Icon GENERIC =
            IconLoader.getIcon("/icons/generic.svg", LattencyIcons.class);

    private LattencyIcons() {}

    public static Icon forColoring(Set<IoCategory> categories, MethodColoring.Origin origin) {
        return forCategories(categories);
    }

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
}
