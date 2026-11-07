package com.datamesh.graphql.pii;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks Open Policy Agent which fields to mask. Fails closed: if OPA cannot be
 * reached or returns nothing, every PII field is redacted.
 */
@Component
public class OpaPolicyClient implements PolicyDecider {

    private static final Logger log = LoggerFactory.getLogger(OpaPolicyClient.class);

    record OpaResponse(Map<String, Map<String, String>> result) {}

    private final boolean enabled;
    private final String path;
    private final RestClient rest;

    public OpaPolicyClient(@Value("${opa.enabled:true}") boolean enabled,
                           @Value("${opa.url:http://opa:8181}") String url,
                           @Value("${opa.path:/v1/data/datamesh/customer/pii/masked}") String path,
                           @Value("${opa.timeout:2s}") Duration timeout) {
        this.enabled = enabled;
        this.path = path;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        this.rest = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    @Override
    public Map<String, Map<String, String>> maskedFields(String user, List<String> roles) {
        if (!enabled) {
            return Map.of();
        }
        try {
            OpaResponse response = rest.post().uri(path)
                .body(Map.of("input", Map.of("user", user == null ? "" : user, "roles", roles)))
                .retrieve()
                .body(OpaResponse.class);
            if (response == null || response.result() == null) {
                log.warn("OPA returned no decision for {}, masking all PII", path);
                return PiiMasker.REDACT_ALL;
            }
            return response.result();
        } catch (RestClientException e) {
            log.warn("OPA call failed ({}), masking all PII", e.getMessage());
            return PiiMasker.REDACT_ALL;
        }
    }
}
