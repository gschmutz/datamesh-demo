package com.datamesh.graphql;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.datamesh.organization.dto.OrganizationDTO;
import com.datamesh.organization.entity.Organization;
import com.datamesh.organization.repository.OrganizationRepository;
import com.datamesh.organization.service.OrganizationService;

/**
 * GraphQL API of the customer service. Reads go through the repositories,
 * writes go through the services so the state events are still published.
 */
@Controller
public class CustomerGraphQlController {

    private final CustomerRepository customers;
    private final OrganizationRepository organizations;
    private final CustomerService customerService;
    private final OrganizationService organizationService;

    public CustomerGraphQlController(CustomerRepository customers, OrganizationRepository organizations,
                                     CustomerService customerService, OrganizationService organizationService) {
        this.customers = customers;
        this.organizations = organizations;
        this.customerService = customerService;
        this.organizationService = organizationService;
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

    @QueryMapping
    public List<Organization> organizations(@Argument int limit) {
        return organizations.findAll(PageRequest.of(0, Math.max(1, limit))).getContent();
    }

    @QueryMapping
    public Organization organizationById(@Argument String id) {
        return organizations.findById(id).orElse(null);
    }

    // --- Mutations -------------------------------------------------------- #

    @MutationMapping
    public Person onboardCustomer(@Argument CustomerDTO input) {
        String id = customerService.onboardCustomer(input);
        return customers.findById(id).orElseThrow();
    }

    @MutationMapping
    public Organization createOrganization(@Argument OrganizationDTO input) {
        String id = organizationService.createOrganization(input);
        return organizations.findById(id).orElseThrow();
    }

    // --- Field resolvers -------------------------------------------------- #

    /** Resolves Customer.organization for all customers in one query. */
    @BatchMapping(typeName = "Customer", field = "organization")
    public Map<Person, Organization> organization(List<Person> persons) {
        List<String> ids = persons.stream().map(Person::getOrganizationId)
            .filter(java.util.Objects::nonNull).distinct().toList();
        Map<String, Organization> byId = organizations.findAllById(ids).stream()
            .collect(Collectors.toMap(Organization::getId, Function.identity()));
        return persons.stream()
            .filter(p -> byId.containsKey(p.getOrganizationId()))
            .collect(Collectors.toMap(Function.identity(), p -> byId.get(p.getOrganizationId())));
    }

    @SchemaMapping(typeName = "Customer", field = "onboardedDate")
    public OffsetDateTime onboardedDate(Person p) { return utc(p.getOnboardedDate()); }

    @SchemaMapping(typeName = "Customer", field = "createdAt")
    public OffsetDateTime customerCreatedAt(Person p) { return utc(p.getCreatedAt()); }

    @SchemaMapping(typeName = "Customer", field = "updatedAt")
    public OffsetDateTime customerUpdatedAt(Person p) { return utc(p.getModifiedAt()); }

    @SchemaMapping(typeName = "Organization", field = "createdAt")
    public OffsetDateTime organizationCreatedAt(Organization o) { return utc(o.getCreatedAt()); }

    @SchemaMapping(typeName = "Organization", field = "updatedAt")
    public OffsetDateTime organizationUpdatedAt(Organization o) { return utc(o.getModifiedAt()); }

    /** The DateTime scalar works on OffsetDateTime, the entities hold Instants. */
    private static OffsetDateTime utc(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }
}
