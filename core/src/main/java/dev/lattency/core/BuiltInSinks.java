package dev.lattency.core;

import java.util.List;

/** Zero-configuration sink definitions for common Java and Spring applications. */
public final class BuiltInSinks {
    public static final String BLOCKING = "org.jetbrains.annotations.Blocking";
    public static final String NON_BLOCKING = "org.jetbrains.annotations.NonBlocking";
    public static final String SPRING_DATA_REPOSITORY =
            "org.springframework.data.repository.Repository";

    private static final List<SinkDefinition> DEFINITIONS = List.of(
            SinkDefinition.supertype(SPRING_DATA_REPOSITORY, IoCategory.DB),
            SinkDefinition.className("org.springframework.web.client.RestClient", IoCategory.HTTP),
            SinkDefinition.className("org.springframework.web.client.RestTemplate", IoCategory.HTTP),
            SinkDefinition.className(
                    "org.springframework.web.reactive.function.client.WebClient", IoCategory.HTTP),
            SinkDefinition.className("java.net.http.HttpClient", IoCategory.HTTP),
            SinkDefinition.packagePrefix("okhttp3", IoCategory.HTTP),
            SinkDefinition.packagePrefix("feign", IoCategory.HTTP),
            SinkDefinition.annotation(
                    "org.springframework.cloud.openfeign.FeignClient", IoCategory.HTTP),
            SinkDefinition.className("javax.sql.DataSource", IoCategory.DB),
            SinkDefinition.supertype("javax.sql.DataSource", IoCategory.DB),
            SinkDefinition.packagePrefix("java.sql", IoCategory.DB),
            SinkDefinition.supertype("java.sql.Connection", IoCategory.DB),
            SinkDefinition.supertype("java.sql.Statement", IoCategory.DB),
            SinkDefinition.supertype("java.sql.PreparedStatement", IoCategory.DB),
            SinkDefinition.className(
                    "org.springframework.jdbc.core.JdbcTemplate", IoCategory.DB),
            SinkDefinition.className(
                    "org.springframework.jdbc.core.simple.JdbcClient", IoCategory.DB),
            SinkDefinition.className(
                    "com.google.cloud.pubsub.v1.Publisher", IoCategory.MESSAGING),
            SinkDefinition.packagePrefix(
                    "org.apache.kafka.clients.producer", IoCategory.MESSAGING),
            SinkDefinition.className(
                    "org.springframework.jms.core.JmsTemplate", IoCategory.MESSAGING),
            SinkDefinition.className(
                    "org.springframework.amqp.rabbit.core.RabbitTemplate", IoCategory.MESSAGING),
            SinkDefinition.className("java.nio.file.Files", IoCategory.FILE),
            SinkDefinition.className("java.io.File", IoCategory.FILE),
            SinkDefinition.className("java.io.FileInputStream", IoCategory.FILE),
            SinkDefinition.className("java.io.FileOutputStream", IoCategory.FILE),
            SinkDefinition.className("java.io.FileReader", IoCategory.FILE),
            SinkDefinition.className("java.io.FileWriter", IoCategory.FILE),
            SinkDefinition.className("java.io.BufferedReader", IoCategory.FILE),
            SinkDefinition.className("java.io.BufferedWriter", IoCategory.FILE),
            SinkDefinition.className("java.io.RandomAccessFile", IoCategory.FILE),
            SinkDefinition.annotation(BLOCKING, IoCategory.GENERIC));

    private BuiltInSinks() {}

    public static List<SinkDefinition> definitions() {
        return DEFINITIONS;
    }
}
