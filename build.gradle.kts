plugins {
    base
    id("org.jetbrains.intellij.platform") apply false
    id("org.jetbrains.changelog")
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

subprojects {
    group = rootProject.group
    version = rootProject.version
}

// https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    version = provider { project.version.toString() }
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}
