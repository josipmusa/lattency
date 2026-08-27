import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(
            providers.gradleProperty("javaVersion").get().toInt())
    }
}

dependencies {
    implementation(project(":core"))

    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
        pluginVerifier()
        zipSigner()
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    // Without this the artifact and sandbox directory are named after the Gradle
    // module ("intellij-adapter") rather than after the plugin.
    projectName = "lattency"

    pluginConfiguration {
        id = "dev.lattency"
        name = "Lattency"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            // Deliberately open-ended: nothing here uses an API that is expected to
            // break, and `verifyPlugin` in CI is what proves that per release. Pinning
            // untilBuild would lock users out of every new IDE until we cut a release.
            untilBuild = provider { null }
        }

        changeNotes = provider {
            with(rootProject.the<org.jetbrains.changelog.ChangelogPluginExtension>()) {
                renderItem(
                    (getOrNull(project.version.toString()) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }

    pluginVerification {
        ides {
            // Every IDE release the Marketplace considers compatible with our declared
            // sinceBuild..untilBuild range. Because untilBuild is open, this is what
            // keeps the open range honest as new IDEs ship.
            recommended()
        }
    }

    // All four are supplied by CI secrets; absent locally, which is fine because
    // signPlugin/publishPlugin are only run from the release workflow.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}
