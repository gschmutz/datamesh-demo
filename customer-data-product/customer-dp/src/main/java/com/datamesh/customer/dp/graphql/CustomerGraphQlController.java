package com.datamesh.customer.dp.graphql;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import com.datamesh.customer.dp.entity.Address;
import com.datamesh.customer.dp.entity.Customer;
import com.datamesh.customer.dp.entity.Phone;
import com.datamesh.customer.dp.repository.CustomerRepository;

/**
 * Read-only GraphQL API on the customer data product (schema customer_dp_v1).
 * There are no mutations: the data is written by the pipeline, not by this API.
 */
@Controller
public class CustomerGraphQlController {

    private final CustomerRepository customers;

    public CustomerGraphQlController(CustomerRepository customers) {
        this.customers = customers;
    }

    // --- Queries ---------------------------------------------------------- #

    @QueryMapping
    public List<Customer> customers(@Argument int limit) {
        return customers.findAll(PageRequest.of(0, Math.max(1, limit))).getContent();
    }

    @QueryMapping
    public Customer customerById(@Argument String id) {
        return customers.findById(id).orElse(null);
    }

    // --- Field resolvers -------------------------------------------------- #

    @SchemaMapping(typeName = "Customer", field = "onboardedDate")
    public OffsetDateTime onboardedDate(Customer c) { return utc(c.getOnboardedDate()); }

    @SchemaMapping(typeName = "Customer", field = "createdAt")
    public OffsetDateTime customerCreatedAt(Customer c) { return utc(c.getCreatedAt()); }

    @SchemaMapping(typeName = "Customer", field = "updatedAt")
    public OffsetDateTime customerUpdatedAt(Customer c) { return utc(c.getModifiedAt()); }

    @SchemaMapping(typeName = "Address", field = "createdAt")
    public OffsetDateTime addressCreatedAt(Address a) { return utc(a.getCreatedAt()); }

    @SchemaMapping(typeName = "Address", field = "updatedAt")
    public OffsetDateTime addressUpdatedAt(Address a) { return utc(a.getModifiedAt()); }

    @SchemaMapping(typeName = "Phone", field = "createdAt")
    public OffsetDateTime phoneCreatedAt(Phone p) { return utc(p.getCreatedAt()); }

    @SchemaMapping(typeName = "Phone", field = "updatedAt")
    public OffsetDateTime phoneUpdatedAt(Phone p) { return utc(p.getModifiedAt()); }

    /** The DateTime scalar works on OffsetDateTime, the entities hold Instants. */
    private static OffsetDateTime utc(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }
}
