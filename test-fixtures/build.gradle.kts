plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.data:spring-data-commons:3.5.4")
    implementation("org.springframework:spring-web:6.2.11")
    implementation("org.springframework:spring-jdbc:6.2.11")
    implementation("org.springframework:spring-context:6.2.11")
    implementation("org.apache.kafka:kafka-clients:4.0.0")
    implementation("org.jetbrains:annotations:26.0.2-1")
    implementation("jakarta.persistence:jakarta.persistence-api:3.2.0")
}
