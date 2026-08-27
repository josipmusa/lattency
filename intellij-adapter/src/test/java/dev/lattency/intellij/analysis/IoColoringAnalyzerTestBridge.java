package dev.lattency.intellij.analysis;

import com.intellij.psi.PsiMethod;

/** Test-only access to deterministic analysis metrics. */
public final class IoColoringAnalyzerTestBridge {
    private IoColoringAnalyzerTestBridge() {}

    public static AnalysisResult analyzeWithoutPlatformCache(PsiMethod method) {
        IoColoringAnalyzer.AnalysisSnapshot snapshot =
                IoColoringAnalyzer.analyzeWithoutPlatformCache(method);
        return new AnalysisResult(snapshot.coloring().isColored(), snapshot.analyzedMethodCount());
    }

    public record AnalysisResult(boolean colored, int analyzedMethodCount) {}
}
