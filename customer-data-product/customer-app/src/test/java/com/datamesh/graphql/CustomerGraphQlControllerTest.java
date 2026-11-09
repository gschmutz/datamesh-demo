package com.datamesh.graphql;

import static org.mockito.Mockito.when;

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

@GraphQlTest(CustomerGraphQlController.class)
@Import(GraphQlConfig.class)
class CustomerGraphQlControllerTest {

    @Autowired GraphQlTester tester;
    @MockitoBean CustomerRepository customers;
    @MockitoBean CustomerService customerService;

    @Test
    void customerByIdReturnsCustomer() {
        var p = new Person();
        p.setId("c1");
        p.setFirstName("Jane");
        when(customers.findById("c1")).thenReturn(Optional.of(p));

        tester.document("{ customerById(id: \"c1\") { id firstName addresses { city } } }")
            .execute()
            .path("customerById.firstName").entity(String.class).isEqualTo("Jane")
            .path("customerById.addresses").entityList(Object.class).hasSize(0);
    }
}
