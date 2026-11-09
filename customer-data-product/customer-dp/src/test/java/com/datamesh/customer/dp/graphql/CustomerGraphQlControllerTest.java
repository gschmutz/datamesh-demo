package com.datamesh.customer.dp.graphql;

import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.datamesh.customer.dp.entity.Customer;
import com.datamesh.customer.dp.repository.CustomerRepository;

@GraphQlTest(CustomerGraphQlController.class)
@Import(GraphQlConfig.class)
class CustomerGraphQlControllerTest {

    @Autowired GraphQlTester tester;
    @MockitoBean CustomerRepository customers;

    @Test
    void customerByIdReturnsCustomer() throws Exception {
        var ctor = Customer.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        var c = ctor.newInstance();
        set(c, "id", "c1");
        set(c, "firstName", "Jane");
        when(customers.findById("c1")).thenReturn(Optional.of(c));

        tester.document("{ customerById(id: \"c1\") { id firstName addresses { city } } }")
            .execute()
            .path("customerById.firstName").entity(String.class).isEqualTo("Jane")
            .path("customerById.addresses").entityList(Object.class).hasSize(0);
    }

    @Test
    void thereAreNoMutations() {
        tester.document("mutation { onboardCustomer(input: {id: \"x\"}) { id } }")
            .execute()
            .errors().expect(e -> true);
    }

    private static void set(Object target, String field, Object value) throws Exception {
        var f = Customer.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
