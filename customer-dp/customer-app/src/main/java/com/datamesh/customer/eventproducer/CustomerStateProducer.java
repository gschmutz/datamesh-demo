package com.datamesh.customer.eventproducer;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.datamesh.customer.avro.CustomerState;
import com.datamesh.customer.entity.Person;
import com.datamesh.common.outbox.EventPublisher;
import com.datamesh.common.outbox.model.OutboxEvent;

import java.util.HashMap;
import java.util.Map;

@Component
public class CustomerStateProducer {

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Value("${customer.topic.name}")
    private String kafkaTopic = "pub.customer.customer.state.v1";

    /**
     * Handle to the Outbox Eventing framework.
     */
    @Autowired
    private EventPublisher eventPublisher;

    public void send(Person person) {

        SchemaRegistryClient schemaRegistryClient = new CachedSchemaRegistryClient(schemaRegistryUrl, 10);
        Map<String, Object> props = new HashMap<>();
        // send correct schemas to the registry, without "avro.java.string"
        props.put(KafkaAvroSerializerConfig.AVRO_REMOVE_JAVA_PROPS_CONFIG, true);
        props.put("schema.registry.url", schemaRegistryUrl);
        KafkaAvroSerializer ser = new KafkaAvroSerializer(schemaRegistryClient, props);

        CustomerState customerState = CustomerConverter.convert(person);

        OutboxEvent outboxEvent = new OutboxEvent(
                person.getId(),
                "customer",
                person.getId(),       // use customerId for the Kafka key
                ser.serialize(kafkaTopic, customerState)
        );

        eventPublisher.fire(outboxEvent);
    }

}
