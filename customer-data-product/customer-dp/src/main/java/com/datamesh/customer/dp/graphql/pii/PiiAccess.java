package com.datamesh.customer.dp.graphql.pii;

import java.util.List;
import java.util.Map;

/** Caller identity for one GraphQL request, with the policy decision fetched at most once. */
public class PiiAccess {

    public static final String CONTEXT_KEY = PiiAccess.class.getName();

    private final String user;
    private final List<String> roles;
    private final PolicyDecider decider;
    private volatile Map<String, Map<String, String>> decision;

    public PiiAccess(String user, List<String> roles, PolicyDecider decider) {
        this.user = user;
        this.roles = roles;
        this.decider = decider;
    }

    /** Returns the value as the caller is allowed to see it. */
    public String mask(String type, String field, String value) {
        return PiiMasker.apply(decision().getOrDefault(type, Map.of()).get(field), value);
    }

    private Map<String, Map<String, String>> decision() {
        Map<String, Map<String, String>> d = decision;
        if (d == null) {
            synchronized (this) {
                if (decision == null) {
                    decision = decider.maskedFields(user, roles);
                }
                d = decision;
            }
        }
        return d;
    }
}
