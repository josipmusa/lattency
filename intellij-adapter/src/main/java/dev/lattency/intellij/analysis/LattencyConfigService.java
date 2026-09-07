package dev.lattency.intellij.analysis;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.Alarm;
import dev.lattency.core.LattencyConfig;
import dev.lattency.core.LattencyConfigLoader;
import dev.lattency.core.SinkMatcher;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Project-level access to the parsed {@code lattency.yml}. Exposes a
 * {@link ModificationTracker} that advances whenever the file changes, so cached
 * analysis results can depend on the configuration without IDE restarts.
 *
 * <p>Two sources drive the tracker. Edits in an editor are picked up from the document,
 * debounced, so the config applies as soon as the user stops typing rather than when
 * the IDE autosaves. External edits, renames and deletions arrive as VFS events. Neither
 * path stats the file on demand: cached-value dependencies are validated on every access,
 * so an on-demand stat would put a blocking filesystem call on the highlighting path once
 * per method per pass.
 */
@Service(Service.Level.PROJECT)
public final class LattencyConfigService implements Disposable {
    public static final String CONFIG_FILE_NAME = "lattency.yml";
    static final String NOTIFICATION_GROUP = "Lattency";
    private static final int EDIT_SETTLE_MS = 1000;

    private static final Logger LOG = Logger.getInstance(LattencyConfigService.class);

    private final Project project;
    private final @Nullable Path configPath;
    private final SimpleModificationTracker tracker = new SimpleModificationTracker();
    private final Alarm editSettled = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    private volatile @Nullable Snapshot snapshot;
    private @Nullable String lastNotifiedWarning;

    public LattencyConfigService(Project project) {
        this.project = project;
        String basePath = project.getBasePath();
        configPath = basePath == null ? null : Path.of(basePath, CONFIG_FILE_NAME);
        if (configPath != null) {
            String watched = FileUtil.toSystemIndependentName(configPath.toString());
            project.getMessageBus().connect(this).subscribe(
                    VirtualFileManager.VFS_CHANGES, new ConfigFileWatcher(watched));
            EditorFactory.getInstance().getEventMulticaster()
                    .addDocumentListener(new ConfigDocumentWatcher(watched), this);
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

    @Override
    public void dispose() {}

    private Snapshot current() {
        long version = tracker.getModificationCount();
        Snapshot local = snapshot;
        if (local == null || local.version != version) {
            LattencyConfig config = configPath == null
                    ? LattencyConfig.defaultsOnly()
                    : read(configPath, this::warn);
            local = new Snapshot(version, config, new SinkMatcher(config));
            snapshot = local;
        }
        return local;
    }

    /** Prefers the editor buffer, which may be ahead of the file on disk. */
    private static LattencyConfig read(Path path, Consumer<String> warningLogger) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(path);
        Document document = file == null ? null : FileDocumentManager.getInstance().getCachedDocument(file);
        return document == null
                ? LattencyConfigLoader.load(path, warningLogger)
                : LattencyConfigLoader.parse(document.getText(), warningLogger);
    }

    private void warn(String message) {
        LOG.warn(message);
        if (message.equals(lastNotifiedWarning)) {
            return;
        }
        lastNotifiedWarning = message;
        NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("Lattency configuration", message, NotificationType.WARNING)
                .notify(project);
    }

    /**
     * Runs on the EDT. Invalidates cached results, parses eagerly so a broken file is
     * reported even with no Java file open, and restarts the daemon: bumping the tracker
     * alone leaves stale icons in open files until each is next re-highlighted.
     */
    private void configChanged() {
        if (project.isDisposed()) {
            return;
        }
        tracker.incModificationCount();
        current();
        // 2025.3 deprecates the no-arg restart() in favour of restart(Object reason), but
        // that overload does not exist in 2025.2, our sinceBuild. Switch when the floor
        // moves; the verifier reports this as a deprecation, not an incompatibility.
        DaemonCodeAnalyzer.getInstance(project).restart();
    }

    private record Snapshot(long version, LattencyConfig config, SinkMatcher matcher) {}

    private final class ConfigDocumentWatcher implements DocumentListener {
        private final String watchedPath;

        private ConfigDocumentWatcher(String watchedPath) {
            this.watchedPath = watchedPath;
        }

        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
            VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());
            if (file != null && watchedPath.equals(file.getPath())) {
                editSettled.cancelAllRequests();
                editSettled.addRequest(LattencyConfigService.this::configChanged, EDIT_SETTLE_MS);
            }
        }
    }

    private final class ConfigFileWatcher implements BulkFileListener {
        private final String watchedPath;

        private ConfigFileWatcher(String watchedPath) {
            this.watchedPath = watchedPath;
        }

        @Override
        public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
            for (VFileEvent event : events) {
                if (touchesConfig(event)) {
                    configChanged();
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
