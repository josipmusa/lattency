plugins {
    base
    id("org.jetbrains.intellij.platform") apply false
}

group = "dev.lattency"
version = "0.1.0-SNAPSHOT"

subprojects {
    group = rootProject.group
    version = rootProject.version
}
