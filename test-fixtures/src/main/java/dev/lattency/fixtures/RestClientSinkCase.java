package dev.lattency.fixtures;

import org.springframework.web.client.RestClient;

public final class RestClientSinkCase {
    private final RestClient client = RestClient.create();

    public String fetch() {
        return client.get().uri("https://example.test").retrieve().body(String.class);
    }
}
