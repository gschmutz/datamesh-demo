package ch.admin.customer.graphql.web;

import ch.admin.customer.graphql.domain.Customer;
import ch.admin.customer.graphql.domain.Organization;
import ch.admin.customer.graphql.repo.CustomerRepository;
import ch.admin.customer.graphql.repo.OrganizationRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class CustomerController {

    private final CustomerRepository customers;
    private final OrganizationRepository organizations;

    public CustomerController(CustomerRepository customers, OrganizationRepository organizations) {
        this.customers = customers;
        this.organizations = organizations;
    }

    // --- Customer queries ------------------------------------------------- #

    @QueryMapping
    public List<Customer> customers(@Argument int limit) {
        return customers.findAll(PageRequest.of(0, Math.max(1, limit))).getContent();
    }

    @QueryMapping
    public Customer customerById(@Argument Long id) {
        return customers.findById(id).orElse(null);
    }

    @QueryMapping
    public Customer customerByLogin(@Argument String login) {
        return customers.findByLogin(login).orElse(null);
    }

    // --- Organization queries --------------------------------------------- #

    @QueryMapping
    public List<Organization> organizations(@Argument int limit) {
        return organizations.findAll(PageRequest.of(0, Math.max(1, limit))).getContent();
    }

    @QueryMapping
    public Organization organizationById(@Argument Long id) {
        return organizations.findById(id).orElse(null);
    }
}
