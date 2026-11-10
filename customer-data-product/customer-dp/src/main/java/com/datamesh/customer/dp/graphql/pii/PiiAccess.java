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

    /**
     * The masking strategy for a field, or null when it is visible. A field the policy
     * names explicitly wins over the wildcard entry of the fail-closed decision, which
     * only applies when {@code allowWildcard} is set.
     */
    public String strategy(String type, String field, boolean allowWildcard) {
        Map<String, Map<String, String>> d = decision();
        String s = d.getOrDefault(type, Map.of()).get(field);
        if (s == null && allowWildcard) {
            s = d.getOrDefault(PiiMasker.WILDCARD, Map.of()).get(PiiMasker.WILDCARD);
        }
        return s;
    }

    /** Returns the value as the caller is allowed to see it. */
    public String mask(String type, String field, String value) {
        return PiiMasker.apply(strategy(type, field, true), value);
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
