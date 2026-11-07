package com.datamesh.graphql.pii;

import java.util.List;
import java.util.Map;

/** Decides which fields must be masked: {graphQlType: {field: strategy}}. */
public interface PolicyDecider {
    Map<String, Map<String, String>> maskedFields(String user, List<String> roles);
}
