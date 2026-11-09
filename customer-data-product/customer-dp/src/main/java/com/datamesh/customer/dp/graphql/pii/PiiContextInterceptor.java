package com.datamesh.customer.dp.graphql.pii;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * Puts the caller identity into the GraphQL context. The headers must be set by a
 * trusted gateway (after it authenticated the caller), never by end users:
 * X-User-Id and X-User-Roles (comma separated). No roles means restricted access.
 */
@Component
public class PiiContextInterceptor implements WebGraphQlInterceptor {

    private final PolicyDecider decider;

    public PiiContextInterceptor(PolicyDecider decider) {
        this.decider = decider;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        String user = request.getHeaders().getFirst("X-User-Id");
        String header = request.getHeaders().getFirst("X-User-Roles");
        List<String> roles = header == null ? List.of()
            : Arrays.stream(header.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        PiiAccess access = new PiiAccess(user, roles, decider);
        request.configureExecutionInput((input, builder) ->
            builder.graphQLContext(Map.of(PiiAccess.CONTEXT_KEY, access)).build());
        return chain.next(request);
    }
}
