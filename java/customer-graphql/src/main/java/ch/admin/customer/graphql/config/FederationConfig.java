package ch.admin.customer.graphql.config;

import ch.admin.customer.graphql.repo.CustomerRepository;
import ch.admin.customer.graphql.repo.OrganizationRepository;
import com.apollographql.federation.graphqljava.Federation;
import com.apollographql.federation.graphqljava._Entity;
import java.util.Map;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Turns this service into an Apollo Federation subgraph (adds _service and _entities). */
@Configuration
public class FederationConfig {

    @Bean
    public GraphQlSourceBuilderCustomizer federationCustomizer(
            CustomerRepository customers, OrganizationRepository organizations) {
        return builder -> builder.schemaFactory((registry, wiring) ->
            Federation.transform(registry, wiring)
                .fetchEntities(env -> env.<java.util.List<Map<String, Object>>>getArgument(_Entity.argumentName)
                    .stream()
                    .map(rep -> {
                        Long id = Long.valueOf(String.valueOf(rep.get("id")));
                        return switch (String.valueOf(rep.get("__typename"))) {
                            case "Customer" -> customers.findById(id).orElse(null);
                            case "Organization" -> organizations.findById(id).orElse(null);
                            default -> null;
                        };
                    })
                    .toList())
                .resolveEntityType(env -> {
                    Object o = env.getObject();
                    String name = o.getClass().getSimpleName();
                    return env.getSchema().getObjectType(name);
                })
                .build());
    }
}
