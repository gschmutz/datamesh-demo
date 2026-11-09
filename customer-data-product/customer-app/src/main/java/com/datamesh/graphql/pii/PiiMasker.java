package com.datamesh.graphql.pii;

import java.util.Map;

/** Applies a masking strategy chosen by the policy to a field value. */
public final class PiiMasker {

    /** Fail-closed decision: every PII field the API knows about is redacted. */
    public static final Map<String, Map<String, String>> REDACT_ALL = Map.of(
        "Customer", Map.of("firstName", "redact", "lastName", "redact",
                           "emailAddress", "redact"),
        "Address", Map.of("street", "redact", "zipCode", "redact"),
        "Phone", Map.of("number", "redact"));

    private PiiMasker() {}

    public static String apply(String strategy, String value) {
        if (strategy == null || value == null) {
            return value;
        }
        return switch (strategy) {
            case "null" -> null;
            case "partial" -> partial(value);
            default -> "***"; // "redact" and any unknown strategy: fail closed
        };
    }

    private static String partial(String v) {
        int at = v.indexOf('@');
        if (at > 0) {
            return v.charAt(0) + "***" + v.substring(at);
        }
        String digits = v.replaceAll("\\D", "");
        if (digits.length() >= 4 && digits.length() * 2 >= v.length()) {
            return "***" + digits.substring(digits.length() - 2);
        }
        return v.isEmpty() ? v : v.charAt(0) + "***";
    }
}
