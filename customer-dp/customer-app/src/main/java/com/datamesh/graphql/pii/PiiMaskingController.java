package com.datamesh.graphql.pii;

import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import com.datamesh.customer.entity.Address;
import com.datamesh.customer.entity.Person;

import graphql.GraphQLContext;

/**
 * Field resolvers for PII fields. Which fields are masked, and how, is decided
 * by the OPA policy, this class only enforces the decision.
 */
@Controller
public class PiiMaskingController {

    @SchemaMapping(typeName = "Customer", field = "firstName")
    public String firstName(Person p, GraphQLContext ctx) { return mask(ctx, "Customer", "firstName", p.getFirstName()); }

    @SchemaMapping(typeName = "Customer", field = "lastName")
    public String lastName(Person p, GraphQLContext ctx) { return mask(ctx, "Customer", "lastName", p.getLastName()); }

    @SchemaMapping(typeName = "Customer", field = "emailAddress")
    public String emailAddress(Person p, GraphQLContext ctx) { return mask(ctx, "Customer", "emailAddress", p.getEmailAddress()); }

    @SchemaMapping(typeName = "Customer", field = "phoneNumber")
    public String phoneNumber(Person p, GraphQLContext ctx) { return mask(ctx, "Customer", "phoneNumber", p.getPhoneNumber()); }

    @SchemaMapping(typeName = "Address", field = "street")
    public String street(Address a, GraphQLContext ctx) { return mask(ctx, "Address", "street", a.getStreet()); }

    @SchemaMapping(typeName = "Address", field = "zipCode")
    public String zipCode(Address a, GraphQLContext ctx) { return mask(ctx, "Address", "zipCode", a.getZipCode()); }

    private static String mask(GraphQLContext ctx, String type, String field, String value) {
        PiiAccess access = ctx.get(PiiAccess.CONTEXT_KEY);
        // No identity in the context (should not happen): fail closed.
        return access == null ? PiiMasker.apply("redact", value) : access.mask(type, field, value);
    }
}
