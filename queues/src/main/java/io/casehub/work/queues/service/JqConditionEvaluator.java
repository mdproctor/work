package io.casehub.work.queues.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.casehub.platform.expression.JQEvaluator;
import io.casehub.work.runtime.model.WorkItem;

@ApplicationScoped
public class JqConditionEvaluator implements WorkItemExpressionEvaluator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    JQEvaluator jqEvaluator;

    @Override
    public String language() {
        return "jq";
    }

    @Override
    public boolean evaluate(final WorkItem wi, final ExpressionDescriptor descriptor) {
        if (descriptor == null || descriptor.expression() == null || descriptor.expression().isBlank()) {
            return false;
        }
        final JsonNode input = MAPPER.valueToTree(toMap(wi));
        return jqEvaluator.eval(descriptor.expression(), input).isTrue();
    }

    private Map<String, Object> toMap(final WorkItem wi) {
        final var map = new HashMap<String, Object>();
        map.put("status", wi.status != null ? wi.status.name() : null);
        map.put("priority", wi.priority != null ? wi.priority.name() : null);
        map.put("assigneeId", wi.assigneeId);
        map.put("candidateGroups", wi.candidateGroups);
        map.put("category", wi.category);
        map.put("title", wi.title);
        map.put("description", wi.description);
        map.put("labels", wi.labels != null
                ? wi.labels.stream().map(l -> l.path).toList()
                : List.of());
        return map;
    }
}
