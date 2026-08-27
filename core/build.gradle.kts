plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(
            providers.gradleProperty("javaVersion").get().toInt())
    }
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation("org.snakeyaml:snakeyaml-engine:2.9")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
