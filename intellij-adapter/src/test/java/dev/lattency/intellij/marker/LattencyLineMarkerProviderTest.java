package dev.lattency.intellij.marker;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import dev.lattency.intellij.LattencyIcons;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class LattencyLineMarkerProviderTest extends LightJavaCodeInsightFixtureTestCase {
    private static final String DECLARATION_TOOLTIP = "Lattency I/O:";
    private static final String CALL_SITE_TOOLTIP = "Lattency I/O call:";

    @Override
    protected void tearDown() throws Exception {
        try {
            String basePath = getProject().getBasePath();
            if (basePath != null) {
                Path config = Path.of(basePath, "lattency.yml");
                Files.deleteIfExists(config);
                refreshConfigInVfs(config);
            }
        } finally {
            super.tearDown();
        }
    }

    public void testMarksSpringDataRepositoryCallAsDb() {
        addSpringDataRepository();

        GutterMark marker = singleDeclarationMarker("""
                package example;
                class Example {
                    void persist(OrderRepository repository) { repository.save("order"); }
                }
                """);

        assertSame(LattencyIcons.DB, marker.getIcon());
        assertTooltip(marker, "persist", "OrderRepository.save", "[DB]");
    }

    public void testMarksHttpSink() {
        myFixture.addClass("""
                package org.springframework.web.client;
                public class RestClient { public String get() { return "ok"; } }
                """);

        GutterMark marker = singleDeclarationMarker("""
                package example;
                class Example {
                    String fetch(org.springframework.web.client.RestClient client) {
                        return client.get();
                    }
                }
                """);

        assertSame(LattencyIcons.HTTP, marker.getIcon());
        assertTooltip(marker, "RestClient.get", "[HTTP]");
    }

    public void testMarksMessagingSink() {
        myFixture.addClass("""
                package org.apache.kafka.clients.producer;
                public class KafkaProducer { public void send(String value) {} }
                """);

        GutterMark marker = singleDeclarationMarker("""
                package example;
                class Example {
                    void publish(org.apache.kafka.clients.producer.KafkaProducer producer) {
                        producer.send("event");
                    }
                }
                """);

        assertSame(LattencyIcons.MESSAGING, marker.getIcon());
        assertTooltip(marker, "KafkaProducer.send", "[MESSAGING]");
    }

    public void testMarksFileSink() {
        addFileApi();
        GutterMark marker = singleDeclarationMarker("""
                package example;
                class Example {
                    String read(java.nio.file.Path path) {
                        return java.nio.file.Files.readString(path);
                    }
                }
                """);

        assertSame(LattencyIcons.FILE, marker.getIcon());
        assertTooltip(marker, "Files.readString", "[FILE]");
    }

    public void testMarksBlockingMethodAsGenericIo() {
        addJetBrainsAnnotation("Blocking");

        GutterMark marker = singleDeclarationMarker("""
                package example;
                class Example {
                    @org.jetbrains.annotations.Blocking void waitForWork() {}
                }
                """);

        assertSame(LattencyIcons.GENERIC, marker.getIcon());
        assertTooltip(marker, "waitForWork", "[GENERIC]");
    }

    public void testNonBlockingSuppressesSink() {
        addJetBrainsAnnotation("NonBlocking");
        configure("""
                package example;
                class Example {
                    @org.jetbrains.annotations.NonBlocking
                    String read(java.nio.file.Path path) throws java.io.IOException {
                        return java.nio.file.Files.readString(path);
                    }
                }
                """);

        assertEmpty(declarationMarkers());
    }

    public void testMarksCustomYamlSink() throws IOException {
        writeConfig("""
                sinks:
                  - match:
                      class: example.RemoteClient
                      method: fetch
                    category: HTTP
                """);
        myFixture.addClass("""
                package example;
                public class RemoteClient { public String fetch() { return "ok"; } }
                """);

        GutterMark marker = singleDeclarationMarker("""
                package example;
                class Example {
                    String load(RemoteClient client) { return client.fetch(); }
                }
                """);

        assertSame(LattencyIcons.HTTP, marker.getIcon());
        assertTooltip(marker, "RemoteClient.fetch", "[HTTP]");
    }

    public void testYamlExclusionSuppressesMarking() throws IOException {
        writeConfig("""
                exclude:
                  - example.excluded
                """);
        configure("""
                package example.excluded;
                class Example {
                    String read(java.nio.file.Path path) throws java.io.IOException {
                        return java.nio.file.Files.readString(path);
                    }
                }
                """);

        assertEmpty(declarationMarkers());
    }

    public void testPropagatesThroughCallChain() {
        addFileApi();
        configure("""
                package example;
                class Example {
                    String top(java.nio.file.Path path) {
                        return middle(path);
                    }
                    String middle(java.nio.file.Path path) {
                        return bottom(path);
                    }
                    String bottom(java.nio.file.Path path) {
                        return java.nio.file.Files.readString(path);
                    }
                }
                """);

        List<GutterMark> markers = declarationMarkers();
        assertSize(3, markers);
        GutterMark topMarker = markerWithTooltipFragment(markers, "top");
        assertTooltip(topMarker,
                "top", "Example.middle", "Example.bottom", "Files.readString", "[FILE]");
        assertSame(LattencyIcons.FILE_TRANSITIVE, topMarker.getIcon());
        assertSame(LattencyIcons.FILE,
                markerWithTooltipFragment(markers, "<br/>bottom &rarr;").getIcon());
    }

    public void testInterfaceCallColoredWhenAnyImplementationColored() {
        addFileApi();
        myFixture.addClass("""
                package example;
                public interface ContentSource { String load(java.nio.file.Path path); }
                """);
        myFixture.addClass("""
                package example;
                public class FileContentSource implements ContentSource {
                    @Override public String load(java.nio.file.Path path) {
                        return java.nio.file.Files.readString(path);
                    }
                }
                """);
        myFixture.addClass("""
                package example;
                public class MemoryContentSource implements ContentSource {
                    @Override public String load(java.nio.file.Path path) { return "memory"; }
                }
                """);

        GutterMark marker = singleDeclarationMarker("""
                package example;
                class Example {
                    String invoke(ContentSource source, java.nio.file.Path path) {
                        return source.load(path);
                    }
                }
                """);

        assertTooltip(marker, "invoke", "FileContentSource.load", "[FILE]");
        assertFalse(marker.getTooltipText().contains("MemoryContentSource"));
    }

    public void testCacheableCalleeMakesAConditionalEdge() {
        addSpringDataRepository();
        addCacheableAnnotation();
        configure("""
                package example;
                class Example {
                    OrderRepository repository;
                    @org.springframework.cache.annotation.Cacheable("orders")
                    String find() { return repository.save("x"); }
                    String caller() { return find(); }
                }
                """);

        List<GutterMark> markers = declarationMarkers();
        assertSize(2, markers);
        GutterMark callerMarker = markerWithTooltipFragment(markers, "caller");
        assertTooltip(callerMarker, "Example.find", "@Cacheable", "conditional");
    }

    public void testCycleTerminatesAndColorsCorrectly() {
        addFileApi();
        configure("""
                package example;
                class Example {
                    void a(java.nio.file.Path path) {
                        b(path);
                        java.nio.file.Files.readString(path);
                    }
                    void b(java.nio.file.Path path) {
                        a(path);
                    }
                }
                """);

        List<GutterMark> markers = declarationMarkers();
        assertSize(2, markers);
        GutterMark cycleMarker = markerWithTooltipFragment(markers, "Example.a");
        assertTooltip(cycleMarker, "Example.a", "Files.readString", "[FILE]");
    }

    public void testChainBeyondConfiguredDepthIsUnmarked() throws IOException {
        writeConfig("depth: 1");
        addFileApi();
        configure("""
                package example;
                class Example {
                    String top(java.nio.file.Path path) {
                        return middle(path);
                    }
                    String middle(java.nio.file.Path path) {
                        return bottom(path);
                    }
                    String bottom(java.nio.file.Path path) {
                        return java.nio.file.Files.readString(path);
                    }
                }
                """);

        List<GutterMark> markers = declarationMarkers();
        assertSize(2, markers);
        for (GutterMark marker : markers) {
            assertFalse(marker.getTooltipText(), marker.getTooltipText().contains("top"));
        }
    }

    public void testCallSiteOfColoredMethodGetsALineMarker() {
        addFileApi();
        configure("""
                package example;
                class Example {
                    String read(java.nio.file.Path path) {
                        return java.nio.file.Files.readString(path);
                    }
                    String use(java.nio.file.Path path) {
                        return read(path);
                    }
                }
                """);

        List<GutterMark> markers = callSiteMarkers();
        GutterMark useCallMarker = markerWithTooltipFragment(markers, "Example.read");
        assertTooltip(useCallMarker, "Example.read", "Files.readString", "[FILE]");
    }

    public void testOneCallSiteMarkerPerLineListingAllCalls() {
        addFileApi();
        addSpringDataRepository();
        configure("""
                package example;
                class Example {
                    OrderRepository repository;
                    String read(java.nio.file.Path path) {
                        return java.nio.file.Files.readString(path);
                    }
                    String persist() {
                        return repository.save("x");
                    }
                    String both(java.nio.file.Path path) {
                        return re<caret>ad(path) + persist();
                    }
                }
                """);

        List<GutterMark> markersAtLine = myFixture.findGuttersAtCaret().stream()
                .filter(marker -> hasTooltipFragment(marker, CALL_SITE_TOOLTIP))
                .toList();

        assertSize(1, markersAtLine);
        assertTooltip(markersAtLine.getFirst(), "Example.read", "[FILE]", "Example.persist", "[DB]");
    }

    public void testRemovingTheSinkAtTheBottomUpdatesMarkersUpTheChain() {
        addFileApi();
        String sinkStatement = "return java.nio.file.Files.readString(path);";
        configure("""
                package example;
                class Example {
                    String top(java.nio.file.Path path) {
                        return middle(path);
                    }
                    String middle(java.nio.file.Path path) {
                        return bottom(path);
                    }
                    String bottom(java.nio.file.Path path) {
                        %s
                    }
                }
                """.formatted(sinkStatement));
        assertSize(3, declarationMarkers());

        Document document = myFixture.getEditor().getDocument();
        int start = document.getText().indexOf(sinkStatement);
        WriteCommandAction.runWriteCommandAction(
                getProject(),
                () -> document.replaceString(
                        start, start + sinkStatement.length(), "return \"memory\";"));
        assertEmpty(declarationMarkers());

        int replacementStart = document.getText().indexOf("return \"memory\";");
        WriteCommandAction.runWriteCommandAction(
                getProject(),
                () -> document.replaceString(
                        replacementStart,
                        replacementStart + "return \"memory\";".length(),
                        sinkStatement));
        List<GutterMark> restored = declarationMarkers();
        assertSize(3, restored);
        assertTooltip(markerWithTooltipFragment(restored, "top"),
                "top", "Example.middle", "Example.bottom", "[FILE]");
    }

    public void testMarkerTracksLiveDocumentEdits() {
        addFileApi();
        String sinkStatement = "return java.nio.file.Files.readString(path);";
        configure("""
                package example;
                class Example {
                    String read(java.nio.file.Path path) {
                        %s
                    }
                }
                """.formatted(sinkStatement));
        assertSize(1, declarationMarkers());

        Document document = myFixture.getEditor().getDocument();
        int start = document.getText().indexOf(sinkStatement);
        WriteCommandAction.runWriteCommandAction(
                getProject(),
                () -> document.replaceString(
                        start, start + sinkStatement.length(), "return \"memory\";"));
        assertEmpty(declarationMarkers());

        int replacementStart = document.getText().indexOf("return \"memory\";");
        WriteCommandAction.runWriteCommandAction(
                getProject(),
                () -> document.replaceString(
                        replacementStart, replacementStart + "return \"memory\";".length(), sinkStatement));
        assertSize(1, declarationMarkers());
    }

    public void testOpeningAFileStreamIsAFileSink() {
        addFileStreams();

        GutterMark marker = singleDeclarationMarker("""
                package example;
                class Example {
                    void open(String name) throws java.io.IOException {
                        new java.io.FileInputStream(name);
                    }
                }
                """);

        assertSame(LattencyIcons.FILE, marker.getIcon());
        assertTooltip(marker, "open", "FileInputStream", "[FILE]");
    }

    public void testNamingAFileOrWrappingAReaderIsNotASink() {
        addFileStreams();
        configure("""
                package example;
                class Example {
                    void describe(String name, java.io.FileReader reader) {
                        new java.io.File(name);
                        new java.io.BufferedReader(reader);
                    }
                }
                """);

        assertEmpty(declarationMarkers());
    }

    public void testConstructionPropagatesThroughProjectConstructors() {
        addFileStreams();
        myFixture.addClass("""
                package example;
                public class Log {
                    public Log(String name) throws java.io.IOException {
                        new java.io.FileWriter(name);
                    }
                }
                """);

        GutterMark marker = singleDeclarationMarker("""
                package example;
                class Example {
                    void start(String name) throws java.io.IOException {
                        new Log(name);
                    }
                }
                """);

        assertSame(LattencyIcons.FILE_TRANSITIVE, marker.getIcon());
        assertTooltip(marker, "start", "Log.Log", "FileWriter", "[FILE]");
    }

    public void testCustomConstructionSinkFromYaml() throws IOException {
        writeConfig("""
                sinks:
                  - match:
                      construction: example.Connection
                    category: DB
                """);
        myFixture.addClass("package example; public class Connection { public Connection(String url) {} }");

        GutterMark marker = singleDeclarationMarker("""
                package example;
                class Example {
                    void connect() { new Connection("jdbc:x"); }
                }
                """);

        assertSame(LattencyIcons.DB, marker.getIcon());
        assertTooltip(marker, "Connection", "[DB]");
    }

    /**
     * A call graph that fans out and re-converges. Without memoization inside a walk,
     * every shared subtree is re-derived once per path reaching it, which is exponential
     * in the depth limit: this shape took over half a second per method to color.
     */
    public void testWideFanOutStaysFast() {
        addFileApi();
        int layers = 4;
        int width = 12;
        StringBuilder source = new StringBuilder("package example;\nclass Fan {\n");
        source.append("  java.nio.file.Path path;\n  void top() {");
        for (int w = 0; w < width; w++) {
            source.append(" L0_").append(w).append("();");
        }
        source.append(" }\n");
        for (int layer = 0; layer < layers; layer++) {
            for (int w = 0; w < width; w++) {
                source.append("  void L").append(layer).append("_").append(w).append("() {");
                if (layer == layers - 1) {
                    source.append(" java.nio.file.Files.readString(path);");
                } else {
                    for (int next = 0; next < width; next++) {
                        source.append(" L").append(layer + 1).append("_").append(next).append("();");
                    }
                }
                source.append(" }\n");
            }
        }
        source.append("}\n");
        configure(source.toString());

        long start = System.nanoTime();
        List<GutterMark> markers = declarationMarkers();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertNotEmpty(markers);
        assertTrue(
                "Coloring a " + width + "-wide, " + layers + "-deep fan-out took "
                        + elapsedMillis + "ms; the walk is re-deriving shared subtrees",
                elapsedMillis < 2_000);
    }

    private void addFileStreams() {
        myFixture.addClass("package java.io; public class IOException extends Exception {}");
        myFixture.addClass("package java.io; public class File { public File(String name) {} }");
        myFixture.addClass("""
                package java.io;
                public class FileInputStream { public FileInputStream(String name) throws IOException {} }
                """);
        myFixture.addClass("""
                package java.io;
                public class FileWriter { public FileWriter(String name) throws IOException {} }
                """);
        myFixture.addClass("package java.io; public class FileReader {}");
        myFixture.addClass("""
                package java.io;
                public class BufferedReader { public BufferedReader(FileReader reader) {} }
                """);
    }

    private GutterMark singleDeclarationMarker(String source) {
        configure(source);
        List<GutterMark> markers = declarationMarkers();
        assertSize(1, markers);
        return markers.getFirst();
    }

    private void configure(String source) {
        myFixture.configureByText("Example.java", source);
    }

    private void addJetBrainsAnnotation(String name) {
        myFixture.addClass("""
                package org.jetbrains.annotations;
                public @interface %s {}
                """.formatted(name));
    }

    private void addFileApi() {
        myFixture.addClass("package java.nio.file; public interface Path {}");
        myFixture.addClass("""
                package java.nio.file;
                public final class Files {
                    public static String readString(Path path) { return "fixture"; }
                }
                """);
    }

    private void addSpringDataRepository() {
        myFixture.addClass("""
                package org.springframework.data.repository;
                public interface Repository<T, ID> {}
                """);
        myFixture.addClass("""
                package example;
                public interface OrderRepository
                        extends org.springframework.data.repository.Repository<String, Long> {
                    String save(String value);
                }
                """);
    }

    private void addCacheableAnnotation() {
        myFixture.addClass("""
                package org.springframework.cache.annotation;
                public @interface Cacheable { String[] value() default {}; }
                """);
    }

    private List<GutterMark> declarationMarkers() {
        return markersWithPrefix(DECLARATION_TOOLTIP);
    }

    private List<GutterMark> callSiteMarkers() {
        return markersWithPrefix(CALL_SITE_TOOLTIP);
    }

    private List<GutterMark> markersWithPrefix(String prefix) {
        return myFixture.findAllGutters().stream()
                .filter(marker -> hasTooltipFragment(marker, prefix))
                .toList();
    }

    private static boolean hasTooltipFragment(GutterMark marker, String fragment) {
        return marker.getTooltipText() != null && marker.getTooltipText().contains(fragment);
    }

    private static GutterMark markerWithTooltipFragment(List<GutterMark> markers, String fragment) {
        return markers.stream()
                .filter(marker -> hasTooltipFragment(marker, fragment))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No marker mentions '" + fragment + "' among: "
                                + markers.stream().map(GutterMark::getTooltipText).toList()));
    }

    /**
     * Writes the project config and refreshes the VFS, the way the IDE does when it
     * regains focus after an external edit. Lattency reloads the file from VFS events,
     * so a write the VFS has not seen yet is deliberately not picked up.
     */
    private void writeConfig(String yaml) throws IOException {
        String basePath = getProject().getBasePath();
        assertNotNull(basePath);
        Files.createDirectories(Path.of(basePath));
        Path config = Path.of(basePath, "lattency.yml");
        Files.writeString(config, yaml);
        refreshConfigInVfs(config);
    }

    private static void refreshConfigInVfs(Path config) {
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(config);
        LocalFileSystem.getInstance().refreshIoFiles(List.of(config.toFile()));
    }

    private static void assertTooltip(GutterMark marker, String... fragments) {
        String tooltip = marker.getTooltipText();
        assertNotNull(tooltip);
        for (String fragment : fragments) {
            assertTrue(tooltip, tooltip.contains(fragment));
        }
    }
}
