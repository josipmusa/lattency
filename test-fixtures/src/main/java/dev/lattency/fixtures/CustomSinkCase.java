package dev.lattency.fixtures;

import dev.lattency.fixtures.support.CustomRemoteClient;

public final class CustomSinkCase {
    private final CustomRemoteClient client = new CustomRemoteClient();

    public String fetch() {
        return client.fetch();
    }
}
