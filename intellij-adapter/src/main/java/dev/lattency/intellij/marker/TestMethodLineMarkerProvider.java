package dev.lattency.intellij.marker;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import dev.lattency.intellij.LattencyIcons;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

/** Initial end-to-end marker: every Java method named {@code test} gets a gutter icon. */
public final class TestMethodLineMarkerProvider extends RelatedItemLineMarkerProvider {
    @Override
    protected void collectNavigationMarkers(
            @NotNull PsiElement element,
            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof PsiIdentifier identifier)
                || !(identifier.getParent() instanceof PsiMethod method)
                || !"test".equals(method.getName())) {
            return;
        }

        var marker = NavigationGutterIconBuilder.create(LattencyIcons.IO_MARKER)
                .setTarget(method)
                .setTooltipText("Lattency: hardcoded I/O marker for method 'test'")
                .createLineMarkerInfo(identifier);
        result.add(marker);
    }
}

