package dev.lattency.intellij.daemon;

import com.intellij.codeInsight.daemon.ChangeLocalityDetector;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Transitive coloring makes a marker depend on the bodies of the methods it calls, so
 * an edit inside one method body can change markers anywhere in the file. Java's own
 * detector would shrink the re-highlighting scope of such an edit to the enclosing
 * code block, leaving the callers' markers stale; widening to the file keeps them
 * fresh. Registered {@code order="first"} so it wins over the Java optimization.
 */
public final class LattencyChangeLocalityDetector implements ChangeLocalityDetector {
    @Override
    public @Nullable PsiElement getChangeHighlightingDirtyScopeFor(
            @NotNull PsiElement changedElement) {
        if (changedElement instanceof PsiCodeBlock
                && changedElement.getParent() instanceof PsiMethod) {
            return changedElement.getContainingFile();
        }
        return null;
    }
}
