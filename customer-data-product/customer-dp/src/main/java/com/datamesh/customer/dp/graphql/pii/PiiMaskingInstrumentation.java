package com.datamesh.customer.dp.graphql.pii;

import java.util.concurrent.CompletionStage;

import org.springframework.stereotype.Component;

import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLTypeUtil;

/**
 * Enforces the masking decision of the OPA policy on every scalar or enum field. Nothing
 * here knows which fields are sensitive: the policy returns {type: {field: strategy}}
 * (derived from the classification in the data contract) and a field is masked when it
 * appears in that result.
 */
@Component
public class PiiMaskingInstrumentation extends SimplePerformantInstrumentation {

    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> fetcher,
                                                InstrumentationFieldFetchParameters parameters,
                                                InstrumentationState state) {
        var step = parameters.getExecutionStepInfo();
        if (!GraphQLTypeUtil.isLeaf(step.getType())) {
            return fetcher;
        }
        String type = step.getObjectType().getName();
        String field = step.getFieldDefinition().getName();
        boolean identifier = "ID".equals(GraphQLTypeUtil.unwrapAll(step.getType()).getName());

        return env -> {
            Object value = fetcher.get(env);
            PiiAccess access = env.getGraphQlContext().get(PiiAccess.CONTEXT_KEY);
            if (value instanceof CompletionStage<?> stage) {
                return stage.thenApply(v -> mask(access, type, field, identifier, v));
            }
            return mask(access, type, field, identifier, value);
        };
    }

    private static Object mask(PiiAccess access, String type, String field, boolean identifier, Object value) {
        if (value == null) {
            return null;
        }
        // No identity in the context (should not happen): fail closed.
        String strategy = access == null
            ? (identifier ? null : PiiMasker.REDACT_ALL.get(PiiMasker.WILDCARD).get(PiiMasker.WILDCARD))
            : access.strategy(type, field, !identifier);
        if (strategy == null) {
            return value;
        }
        if (value instanceof String s) {
            return PiiMasker.apply(strategy, s);
        }
        // A field the policy names explicitly but that is no text (e.g. a date) cannot hold
        // a mask; the wildcard of the fail-closed decision leaves such values alone.
        boolean explicit = access != null && access.strategy(type, field, false) != null;
        return explicit ? null : value;
    }
}
