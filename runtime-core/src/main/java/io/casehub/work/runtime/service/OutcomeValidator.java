package io.casehub.work.runtime.service;

import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.work.api.Outcome;
import io.casehub.work.runtime.event.WorkItemContextBuilder;
import io.casehub.work.runtime.model.OutcomeCodecs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OutcomeValidator {

    private final ExpressionEngineRegistry expressionRegistry;

    public OutcomeValidator(final ExpressionEngineRegistry expressionRegistry) {
        this.expressionRegistry = expressionRegistry;
    }

    @SuppressWarnings("unchecked")
    public void validate(final io.casehub.work.api.WorkItem item, final String outcome,
                         final String resolution, final String reason, final String actorId) {
        if (item.permittedOutcomes() == null) {
            return;
        }
        if (outcome == null || outcome.isBlank()) {
            throw new IllegalArgumentException(
                    "outcome is required — this WorkItem was created from a template that declares named outcomes");
        }
        if (outcome.length() > 255) {
            throw new IllegalArgumentException("outcome exceeds maximum length of 255 characters");
        }

        final List<Outcome> definitions = OutcomeCodecs.decodePermittedOutcomes(item.permittedOutcomes());
        if (definitions == null) {
            throw new IllegalStateException(
                    "permittedOutcomes on WorkItem " + item.id() + " is non-null but failed to decode — data integrity error");
        }

        final Optional<Outcome> match = definitions.stream()
                                                   .filter(o -> outcome.equals(o.name()))
                                                   .findFirst();

        if (match.isEmpty()) {
            final List<String> names = definitions.stream().map(Outcome::name).toList();
            throw new IllegalArgumentException(
                    "outcome '" + outcome + "' is not permitted; allowed values: " + names);
        }

        final String condition = match.get().condition();
        if (condition != null && !condition.isBlank()) {
            final Map<String, Object> context = new HashMap<>(WorkItemContextBuilder.toMap(item));
            context.put("resolution", resolution);
            context.put("reason", reason);
            context.put("actorId", actorId);
            var compiled = expressionRegistry.compile("jexl", condition,
                                                      (Class<Map<String, Object>>) (Class<?>) Map.class, Boolean.class);
            if (!Boolean.TRUE.equals(compiled.eval(context))) {
                throw new IllegalArgumentException(
                        "outcome '" + outcome + "' condition not satisfied (check template definition)");
            }
        }
    }
}
