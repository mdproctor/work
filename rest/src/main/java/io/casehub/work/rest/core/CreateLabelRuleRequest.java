package io.casehub.work.rest.core;

import java.util.List;

public record CreateLabelRuleRequest(String name, String description,
        String conditionLanguage, String conditionExpression,
        List<LabelActionDto> actions, String triggerEvents, String scope) {

    public record LabelActionDto(String type, String label) {
    }
}
