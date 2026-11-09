package com.datamesh.customer.dp.graphql.pii;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PiiMaskingTest {

    @Test
    void strategies() {
        assertEquals("***", PiiMasker.apply("redact", "x"));
        assertEquals("j***@acme.com", PiiMasker.apply("partial", "jane@acme.com"));
        assertEquals("***67", PiiMasker.apply("partial", "+41 79 123 45 67"));
        assertEquals("J***", PiiMasker.apply("partial", "Jane"));
        assertNull(PiiMasker.apply("null", "x"));
        assertEquals("clear", PiiMasker.apply(null, "clear"));
        assertEquals("***", PiiMasker.apply("unknown", "x"));
        assertNull(PiiMasker.apply("redact", null));
    }

    @Test
    void decisionIsFetchedOnceAndApplied() {
        var calls = new AtomicInteger();
        PolicyDecider decider = (user, roles) -> {
            calls.incrementAndGet();
            return Map.of("Customer", Map.of("emailAddress", "partial"));
        };
        var access = new PiiAccess("bob", List.of("support-agent"), decider);

        assertEquals("j***@acme.com", access.mask("Customer", "emailAddress", "jane@acme.com"));
        assertEquals("Jane", access.mask("Customer", "firstName", "Jane"));
        assertEquals("1", access.mask("Address", "street", "1"));
        assertEquals(1, calls.get());
    }
}
