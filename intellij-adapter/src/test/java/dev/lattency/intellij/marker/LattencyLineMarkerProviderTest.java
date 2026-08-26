package dev.lattency.intellij.marker;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
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
                Files.deleteIfExists(Path.of(basePath, "lattency.yml"));
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

    private void writeConfig(String yaml) throws IOException {
        String basePath = getProject().getBasePath();
        assertNotNull(basePath);
        Files.createDirectories(Path.of(basePath));
        Files.writeString(Path.of(basePath, "lattency.yml"), yaml);
    }

    private static void assertTooltip(GutterMark marker, String... fragments) {
        String tooltip = marker.getTooltipText();
        assertNotNull(tooltip);
        for (String fragment : fragments) {
            assertTrue(tooltip, tooltip.contains(fragment));
        }
    }
}
