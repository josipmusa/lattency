package dev.lattency.fixtures;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

public final class KafkaSinkCase {
    private final Producer<String, String> producer;

    public KafkaSinkCase(Producer<String, String> producer) {
        this.producer = producer;
    }

    public void publish(String payload) {
        producer.send(new ProducerRecord<>("orders", payload));
    }
}
