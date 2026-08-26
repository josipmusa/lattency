package dev.lattency.intellij.analysis;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import dev.lattency.core.LattencyConfig;
import dev.lattency.core.LattencyConfigLoader;
import dev.lattency.core.SinkMatcher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Project-level access to the parsed {@code lattency.yml}. Exposes a
 * {@link ModificationTracker} that advances whenever the file on disk changes, so
 * cached analysis results can depend on the configuration without IDE restarts.
 */
@Service(Service.Level.PROJECT)
public final class LattencyConfigService {
    private static final Logger LOG = Logger.getInstance(LattencyConfigService.class);

    private final Project project;
    private final ConfigFileTracker tracker = new ConfigFileTracker();
    private volatile Snapshot snapshot;

    public LattencyConfigService(Project project) {
        this.project = project;
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
            LattencyConfig config = load();
            local = new Snapshot(version, config, new SinkMatcher(config));
            snapshot = local;
        }
        return local;
    }

    private LattencyConfig load() {
        Path path = configPath();
        if (path == null) {
            return LattencyConfig.defaultsOnly();
        }
        return LattencyConfigLoader.load(path, LOG::warn);
    }

    private Path configPath() {
        String basePath = project.getBasePath();
        return basePath == null ? null : Path.of(basePath, "lattency.yml");
    }

    private record Snapshot(long version, LattencyConfig config, SinkMatcher matcher) {}

    private final class ConfigFileTracker implements ModificationTracker {
        private long knownStamp = Long.MIN_VALUE;
        private long version;

        @Override
        public synchronized long getModificationCount() {
            long stamp = currentStamp();
            if (stamp != knownStamp) {
                knownStamp = stamp;
                version++;
            }
            return version;
        }

        private long currentStamp() {
            Path path = configPath();
            if (path == null) {
                return -1;
            }
            try {
                BasicFileAttributes attributes =
                        Files.readAttributes(path, BasicFileAttributes.class);
                return attributes.lastModifiedTime().toMillis() * 31 + attributes.size();
            } catch (IOException missingOrUnreadable) {
                return -1;
            }
        }
    }
}
