package com.datamesh.customer.dp.graphql;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import graphql.ExecutionInput;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.datamesh.customer.dp.entity.Customer;
import com.datamesh.customer.dp.graphql.pii.PiiAccess;
import com.datamesh.customer.dp.graphql.pii.PiiMaskingInstrumentation;
import com.datamesh.customer.dp.graphql.pii.PolicyDecider;
import com.datamesh.customer.dp.repository.CustomerRepository;

@GraphQlTest(CustomerGraphQlController.class)
@Import({GraphQlConfig.class, PiiMaskingInstrumentation.class, CustomerGraphQlControllerTest.Identity.class})
class CustomerGraphQlControllerTest {

    @Autowired GraphQlTester tester;
    @MockitoBean CustomerRepository customers;
    @MockitoBean PolicyDecider decider; // returns no masked fields unless stubbed

    /** The tester bypasses the web interceptor, so put the caller identity in the context here. */
    @TestConfiguration
    static class Identity {
        @Bean
        SimplePerformantInstrumentation identity(PolicyDecider decider) {
            return new SimplePerformantInstrumentation() {
                @Override
                public ExecutionInput instrumentExecutionInput(ExecutionInput input,
                        InstrumentationExecutionParameters parameters, InstrumentationState state) {
                    input.getGraphQLContext().put(PiiAccess.CONTEXT_KEY, new PiiAccess("bob", List.of(), decider));
                    return input;
                }
            };
        }
    }

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
    void fieldsNamedByThePolicyAreMasked() throws Exception {
        var ctor = Customer.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        var c = ctor.newInstance();
        set(c, "id", "c1");
        set(c, "firstName", "Jane");
        set(c, "login", "jane");
        when(customers.findById("c1")).thenReturn(Optional.of(c));
        when(decider.maskedFields(any(), anyList()))
            .thenReturn(Map.of("Customer", Map.of("firstName", "redact")));

        tester.document("{ customerById(id: \"c1\") { id login firstName } }")
            .execute()
            .path("customerById.firstName").entity(String.class).isEqualTo("***")
            .path("customerById.login").entity(String.class).isEqualTo("jane")
            .path("customerById.id").entity(String.class).isEqualTo("c1");
    }

    @Test
    void failClosedMasksTextButNotIds() throws Exception {
        var ctor = Customer.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        var c = ctor.newInstance();
        set(c, "id", "c1");
        set(c, "login", "jane");
        when(customers.findById("c1")).thenReturn(Optional.of(c));
        when(decider.maskedFields(any(), anyList()))
            .thenReturn(Map.of("*", Map.of("*", "redact")));

        tester.document("{ customerById(id: \"c1\") { id login } }")
            .execute()
            .path("customerById.id").entity(String.class).isEqualTo("c1")
            .path("customerById.login").entity(String.class).isEqualTo("***");
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
