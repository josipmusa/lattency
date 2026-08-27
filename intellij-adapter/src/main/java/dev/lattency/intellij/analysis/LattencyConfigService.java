package dev.lattency.intellij.analysis;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import dev.lattency.core.LattencyConfig;
import dev.lattency.core.LattencyConfigLoader;
import dev.lattency.core.SinkMatcher;
import java.nio.file.Path;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Project-level access to the parsed {@code lattency.yml}. Exposes a
 * {@link ModificationTracker} that advances whenever the file changes, so cached
 * analysis results can depend on the configuration without IDE restarts.
 *
 * <p>The tracker is driven by VFS events rather than by stat-ing the file on demand:
 * cached-value dependencies are validated on every access, so an on-demand stat would
 * put a blocking filesystem call on the highlighting path once per method per pass.
 */
@Service(Service.Level.PROJECT)
public final class LattencyConfigService {
    public static final String CONFIG_FILE_NAME = "lattency.yml";

    private static final Logger LOG = Logger.getInstance(LattencyConfigService.class);

    private final @Nullable Path configPath;
    private final SimpleModificationTracker tracker = new SimpleModificationTracker();
    private volatile @Nullable Snapshot snapshot;

    public LattencyConfigService(Project project) {
        String basePath = project.getBasePath();
        configPath = basePath == null ? null : Path.of(basePath, CONFIG_FILE_NAME);
        if (configPath != null) {
            String watched = configPath.toString();
            project.getMessageBus().connect().subscribe(
                    VirtualFileManager.VFS_CHANGES, new ConfigFileWatcher(watched));
        }
    }

    public static LattencyConfigService getInstance(Project project) {
        return project.getService(LattencyConfigService.class);
    }

    public LattencyConfig config() {
        return current().config;
    }

    public SinkMatcher matcher() {
        return current().matcher;
    }

    /** Advances whenever lattency.yml changes; use as a cached-value dependency. */
    public ModificationTracker tracker() {
        return tracker;
    }

    private Snapshot current() {
        long version = tracker.getModificationCount();
        Snapshot local = snapshot;
        if (local == null || local.version != version) {
            LattencyConfig config = configPath == null
                    ? LattencyConfig.defaultsOnly()
                    : LattencyConfigLoader.load(configPath, LOG::warn);
            local = new Snapshot(version, config, new SinkMatcher(config));
            snapshot = local;
        }
        return local;
    }

    private record Snapshot(long version, LattencyConfig config, SinkMatcher matcher) {}

    private final class ConfigFileWatcher implements BulkFileListener {
        private final String watchedPath;

        private ConfigFileWatcher(String watchedPath) {
            this.watchedPath = watchedPath;
        }

        @Override
        public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
            for (VFileEvent event : events) {
                if (touchesConfig(event)) {
                    tracker.incModificationCount();
                    return;
                }
            }
        }

        private boolean touchesConfig(VFileEvent event) {
            if (watchedPath.equals(event.getPath())) {
                return true;
            }
            // A rename or move away from lattency.yml also changes the effective config.
            if (event instanceof VFilePropertyChangeEvent renamed) {
                return watchedPath.equals(renamed.getOldPath());
            }
            if (event instanceof VFileMoveEvent moved) {
                return watchedPath.equals(moved.getOldPath());
            }
            return false;
        }
    }
}
