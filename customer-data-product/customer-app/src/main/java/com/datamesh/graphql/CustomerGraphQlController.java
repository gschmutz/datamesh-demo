package com.datamesh.graphql;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import com.datamesh.customer.dto.CustomerDTO;
import com.datamesh.customer.entity.Person;
import com.datamesh.customer.repository.CustomerRepository;
import com.datamesh.customer.service.CustomerService;

/**
 * GraphQL API of the customer service. Reads go through the repositories,
 * writes go through the services so the state events are still published.
 */
@Controller
public class CustomerGraphQlController {

    private final CustomerRepository customers;
    private final CustomerService customerService;

    public CustomerGraphQlController(CustomerRepository customers, CustomerService customerService) {
        this.customers = customers;
        this.customerService = customerService;
    }

    // --- Queries ---------------------------------------------------------- #

    @QueryMapping
    public List<Person> customers(@Argument int limit) {
        return customers.findAll(PageRequest.of(0, Math.max(1, limit))).getContent();
    }

    @QueryMapping
    public Person customerById(@Argument String id) {
        return customers.findById(id).orElse(null);
    }

    // --- Mutations -------------------------------------------------------- #

    @MutationMapping
    public Person onboardCustomer(@Argument CustomerDTO input) {
        String id = customerService.onboardCustomer(input);
        return customers.findById(id).orElseThrow();
    }

    // --- Field resolvers -------------------------------------------------- #

    @SchemaMapping(typeName = "Customer", field = "onboardedDate")
    public OffsetDateTime onboardedDate(Person p) { return utc(p.getOnboardedDate()); }

    @SchemaMapping(typeName = "Customer", field = "createdAt")
    public OffsetDateTime customerCreatedAt(Person p) { return utc(p.getCreatedAt()); }

    @SchemaMapping(typeName = "Customer", field = "updatedAt")
    public OffsetDateTime customerUpdatedAt(Person p) { return utc(p.getModifiedAt()); }

    /** The DateTime scalar works on OffsetDateTime, the entities hold Instants. */
    private static OffsetDateTime utc(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }
}
