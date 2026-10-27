package com.datamesh.organization.eventproducer;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.datamesh.common.outbox.EventPublisher;
import com.datamesh.common.outbox.model.OutboxEvent;
import com.datamesh.organization.avro.OrganizationState;
import com.datamesh.organization.entity.Organization;

import java.util.HashMap;
import java.util.Map;

@Component
public class OrganizationStateProducer {

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Value("${organization.topic.name}")
    private String kafkaTopic;

    /**
     * Handle to the Outbox Eventing framework.
     */
    @Autowired
    private EventPublisher eventPublisher;

    public void send(Organization organization) {

        SchemaRegistryClient schemaRegistryClient = new CachedSchemaRegistryClient(schemaRegistryUrl, 10);
        Map<String, Object> props = new HashMap<>();
        // send correct schemas to the registry, without "avro.java.string"
        props.put(KafkaAvroSerializerConfig.AVRO_REMOVE_JAVA_PROPS_CONFIG, true);
        props.put("schema.registry.url", schemaRegistryUrl);
        KafkaAvroSerializer ser = new KafkaAvroSerializer(schemaRegistryClient, props);

        OrganizationState organizationState = OrganizationConverter.convert(organization);

        String id = String.valueOf(organization.getId());
        OutboxEvent outboxEvent = new OutboxEvent(
                id,
                "organization",
                id,                   // use organizationId for the Kafka key
                ser.serialize(kafkaTopic, organizationState)
        );

        eventPublisher.fire(outboxEvent);
    }
}
