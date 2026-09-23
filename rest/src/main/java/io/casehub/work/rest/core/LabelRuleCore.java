package io.casehub.work.rest.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.transaction.Transactional;

import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.platform.api.label.LabelAction;
import io.casehub.platform.api.label.LabelRule;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.runtime.filter.LabelRuleEngine;
import io.casehub.work.runtime.filter.LabelRuleEntity;
import io.casehub.work.runtime.repository.LabelRuleStore;

public class LabelRuleCore {

    private final LabelRuleStore labelRuleStore;
    private final ExpressionEngineRegistry expressionRegistry;
    private final List<LabelRule> permanentRules;
    private final WorkItemStore workItemStore;
    private final LabelRuleEngine labelRuleEngine;

    public LabelRuleCore(LabelRuleStore labelRuleStore,
            ExpressionEngineRegistry expressionRegistry,
            List<LabelRule> permanentRules,
            WorkItemStore workItemStore,
            LabelRuleEngine labelRuleEngine) {
        this.labelRuleStore = labelRuleStore;
        this.expressionRegistry = expressionRegistry;
        this.permanentRules = permanentRules;
        this.workItemStore = workItemStore;
        this.labelRuleEngine = labelRuleEngine;
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (LabelRuleEntity r : labelRuleStore.scanAll()) {
            result.add(toPersistedResponse(r));
        }
        permanentRules.forEach(r -> result.add(toPermanentResponse(r)));
        return result;
    }

    @Transactional
    public Map<String, Object> create(CreateLabelRuleRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        if (req.conditionLanguage() == null || req.conditionLanguage().isBlank()) {
            throw new IllegalArgumentException("conditionLanguage required");
        }
        if (req.conditionExpression() == null || req.conditionExpression().isBlank()) {
            throw new IllegalArgumentException("conditionExpression required");
        }

        expressionRegistry.validate(req.conditionLanguage(), req.conditionExpression());

        LabelRuleEntity rule = new LabelRuleEntity();
        rule.name = req.name();
        rule.description = req.description();
        rule.conditionLanguage = req.conditionLanguage();
        rule.conditionExpression = req.conditionExpression();
        rule.actionsJson = req.actions() != null
                ? LabelRuleEntity.serializeActions(toActions(req.actions()))
                : "[]";
        rule.triggerEvents = req.triggerEvents() != null ? req.triggerEvents() : "";
        if (req.scope() != null && !req.scope().isBlank()) {
            rule.scope = io.casehub.platform.api.path.Path.parse(req.scope());
        }
        rule.enabled = true;
        labelRuleStore.put(rule);
        return Map.of("id", rule.id, "name", rule.name, "enabled", rule.enabled);
    }

    @Transactional
    public java.util.Optional<Map<String, Object>> update(UUID id, CreateLabelRuleRequest req) {
        LabelRuleEntity rule = labelRuleStore.get(id).orElse(null);
        if (rule == null) {
            return java.util.Optional.empty();
        }
        if (req.name() != null) {
            rule.name = req.name();
        }
        if (req.conditionExpression() != null) {
            rule.conditionExpression = req.conditionExpression();
        }
        if (req.actions() != null) {
            rule.actionsJson = LabelRuleEntity.serializeActions(toActions(req.actions()));
        }
        if (req.description() != null) {
            rule.description = req.description();
        }
        if (req.triggerEvents() != null) {
            rule.triggerEvents = req.triggerEvents();
        }
        return java.util.Optional.of(Map.of("id", rule.id, "name", rule.name));
    }

    @Transactional
    public boolean delete(UUID id) {
        if (!labelRuleStore.delete(id)) {
            return false;
        }
        for (var wi : workItemStore.scanAll()) {
            var entity = io.casehub.work.runtime.repository.WorkItemEntityMapper.toEntity(wi);
            labelRuleEngine.evaluate(entity, io.casehub.work.runtime.event.WorkItemContextBuilder.toMap(wi), "UPDATE");
            workItemStore.put(io.casehub.work.runtime.repository.WorkItemEntityMapper.toDomain(entity));
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> evaluate(Map<String, Object> req) {
        String language = (String) req.get("conditionLanguage");
        String expression = (String) req.get("conditionExpression");
        if (language == null || expression == null) {
            throw new IllegalArgumentException("conditionLanguage and conditionExpression required");
        }
        var compiled = expressionRegistry.compile(language, expression,
                (Class<Map<String, Object>>) (Class<?>) Map.class, Boolean.class);
        Map<String, Object> context = (Map<String, Object>) req.getOrDefault("context", Map.of());
        Boolean result = compiled.eval(context);
        return Map.of("matches", Boolean.TRUE.equals(result));
    }

    private List<LabelAction> toActions(List<CreateLabelRuleRequest.LabelActionDto> dtos) {
        return dtos.stream().map(dto -> {
            if ("Add".equals(dto.type())) {
                return (LabelAction) new LabelAction.Add(dto.label());
            } else {
                return (LabelAction) new LabelAction.Remove(dto.label());
            }
        }).toList();
    }

    private Map<String, Object> toPersistedResponse(LabelRuleEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id);
        m.put("name", r.name);
        m.put("description", r.description);
        m.put("enabled", r.enabled);
        m.put("conditionLanguage", r.conditionLanguage);
        m.put("conditionExpression", r.conditionExpression);
        m.put("actionsJson", r.actionsJson);
        m.put("triggerEvents", r.triggerEvents);
        m.put("scope", r.scope != null ? r.scope.value() : null);
        m.put("source", "persisted");
        m.put("createdAt", r.createdAt);
        return m;
    }

    private Map<String, Object> toPermanentResponse(LabelRule r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", r.name());
        m.put("triggerEvents", r.triggerEvents());
        m.put("source", "permanent");
        return m;
    }
}
