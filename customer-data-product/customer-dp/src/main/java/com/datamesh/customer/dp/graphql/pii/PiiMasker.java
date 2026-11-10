package com.datamesh.customer.dp.graphql.pii;

import java.util.Map;

/** Applies a masking strategy chosen by the policy to a field value. */
public final class PiiMasker {

    /** Matches every type or field, used by the fail-closed decision. */
    public static final String WILDCARD = "*";

    /**
     * Fail-closed decision, used when the policy cannot be evaluated: every free-text
     * (String) field except identifiers is redacted, whatever the policy would have classified.
     */
    public static final Map<String, Map<String, String>> REDACT_ALL =
        Map.of(WILDCARD, Map.of(WILDCARD, "redact"));

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
