package dev.lattency.intellij.marker;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.List;

public final class TestMethodLineMarkerProviderTest extends BasePlatformTestCase {
    public void testMarksOnlyMethodNamedTest() {
        myFixture.configureByText(
                "Example.java",
                """
                class Example {
                    void test() {}
                    void helper() {}
                }
                """);

        List<GutterMark> markers = myFixture.findAllGutters();

        assertSize(1, markers);
        assertEquals(
                "Lattency: hardcoded I/O marker for method 'test'",
                markers.getFirst().getTooltipText());
    }
}
