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
    public void testMarksSpringDataRepositoryCallAsDb() {
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

        GutterMark marker = singleMarker("""
                package example;
                class Example {
                    void persist(OrderRepository repository) { repository.save("order"); }
                }
                """);

        assertSame(LattencyIcons.DB, marker.getIcon());
        assertTooltip(marker, "OrderRepository.save", "[DB]");
    }

    public void testMarksHttpSink() {
        myFixture.addClass("""
                package org.springframework.web.client;
                public class RestClient { public String get() { return "ok"; } }
                """);

        GutterMark marker = singleMarker("""
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

        GutterMark marker = singleMarker("""
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
        GutterMark marker = singleMarker("""
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

        GutterMark marker = singleMarker("""
                package example;
                class Example {
                    @org.jetbrains.annotations.Blocking void waitForWork() {}
                }
                """);

        assertSame(LattencyIcons.GENERIC, marker.getIcon());
        assertTooltip(marker, "waitForWork()", "[GENERIC]");
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

        assertEmpty(myFixture.findAllGutters());
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

        GutterMark marker = singleMarker("""
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

        assertEmpty(myFixture.findAllGutters());
    }

    public void testDoesNotPropagateThroughCallChain() {
        addFileApi();
        configure("""
                package example;
                class Example {
                    // lattency-future: top should inherit FILE from bottom later.
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

        List<GutterMark> markers = lattencyMarkers();
        assertSize(1, markers);
        assertTooltip(markers.getFirst(), "Files.readString", "[FILE]");
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
        assertSize(1, lattencyMarkers());

        Document document = myFixture.getEditor().getDocument();
        int start = document.getText().indexOf(sinkStatement);
        WriteCommandAction.runWriteCommandAction(
                getProject(),
                () -> document.replaceString(
                        start, start + sinkStatement.length(), "return \"memory\";"));
        assertEmpty(lattencyMarkers());

        int replacementStart = document.getText().indexOf("return \"memory\";");
        WriteCommandAction.runWriteCommandAction(
                getProject(),
                () -> document.replaceString(
                        replacementStart, replacementStart + "return \"memory\";".length(), sinkStatement));
        assertSize(1, lattencyMarkers());
    }

    private GutterMark singleMarker(String source) {
        configure(source);
        List<GutterMark> markers = lattencyMarkers();
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

    private List<GutterMark> lattencyMarkers() {
        return myFixture.findAllGutters().stream()
                .filter(marker -> marker.getTooltipText() != null
                        && marker.getTooltipText().contains("Lattency direct I/O"))
                .toList();
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
