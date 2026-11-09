package com.datamesh.graphql;

import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;

import com.datamesh.customer.entity.Person;
import com.datamesh.customer.repository.CustomerRepository;
import com.datamesh.customer.service.CustomerService;
import com.datamesh.organization.entity.Organization;
import com.datamesh.organization.repository.OrganizationRepository;
import com.datamesh.organization.service.OrganizationService;

@GraphQlTest(CustomerGraphQlController.class)
@Import(GraphQlConfig.class)
class CustomerGraphQlControllerTest {

    @Autowired GraphQlTester tester;
    @MockitoBean CustomerRepository customers;
    @MockitoBean OrganizationRepository organizations;
    @MockitoBean CustomerService customerService;
    @MockitoBean OrganizationService organizationService;

    @Test
    void customerByIdResolvesOrganization() {
        var org = new Organization();
        org.setId("o1");
        org.setName("Acme");
        var p = new Person();
        p.setId("c1");
        p.setFirstName("Jane");
        p.setOrganizationId("o1");
        when(customers.findById("c1")).thenReturn(Optional.of(p));
        when(organizations.findAllById(List.of("o1"))).thenReturn(List.of(org));

        tester.document("{ customerById(id: \"c1\") { id firstName organization { name } addresses { city } } }")
            .execute()
            .path("customerById.firstName").entity(String.class).isEqualTo("Jane")
            .path("customerById.organization.name").entity(String.class).isEqualTo("Acme")
            .path("customerById.addresses").entityList(Object.class).hasSize(0);
    }
}
