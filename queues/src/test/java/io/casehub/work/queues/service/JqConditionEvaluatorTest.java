package io.casehub.work.queues.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.LabelPersistence;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemLabel;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.platform.expression.JQEvaluator;
import io.casehub.platform.expression.MockConfigManager;
import io.casehub.platform.expression.MockSecretManager;
import io.quarkus.test.component.QuarkusComponentTest;

@QuarkusComponentTest({ JqConditionEvaluator.class, JQEvaluator.class, MockSecretManager.class, MockConfigManager.class })
class JqConditionEvaluatorTest {

    @Inject
    JqConditionEvaluator evaluator;

    @Test
    void language_isJq() {
        assertThat(evaluator.language()).isEqualTo("jq");
    }

    @Test
    void evaluate_priorityHigh_matchesHigh() {
        final WorkItem wi = wi(WorkItemStatus.PENDING, WorkItemPriority.HIGH, null);
        assertThat(evaluator.evaluate(wi, ExpressionDescriptor.of("jq", ".priority == \"HIGH\""))).isTrue();
    }

    @Test
    void evaluate_priorityHigh_notNormal() {
        final WorkItem wi = wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null);
        assertThat(evaluator.evaluate(wi, ExpressionDescriptor.of("jq", ".priority == \"HIGH\""))).isFalse();
    }

    @Test
    void evaluate_statusPending() {
        final WorkItem wi = wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null);
        assertThat(evaluator.evaluate(wi, ExpressionDescriptor.of("jq", ".status == \"PENDING\""))).isTrue();
    }

    @Test
    void evaluate_assigneeNull() {
        final WorkItem wi = wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null);
        assertThat(evaluator.evaluate(wi, ExpressionDescriptor.of("jq", ".assigneeId == null"))).isTrue();
    }

    @Test
    void evaluate_malformed_returnsFalse() {
        final WorkItem wi = wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null);
        assertThat(evaluator.evaluate(wi, ExpressionDescriptor.of("jq", "not valid jq @@@"))).isFalse();
    }

    @Test
    void evaluate_candidateGroupsFilter_matchesGroup() {
        final WorkItem wi = wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null);
        wi.candidateGroups = "legal,finance";
        assertThat(evaluator.evaluate(wi,
                ExpressionDescriptor.of("jq", ".candidateGroups | contains(\"legal\")"))).isTrue();
        assertThat(evaluator.evaluate(wi,
                ExpressionDescriptor.of("jq", ".candidateGroups | contains(\"hr\")"))).isFalse();
    }

    @Test
    void evaluate_labelCheck_matchesWorkItemWithLabel() {
        final WorkItem wi = wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null);
        wi.labels.add(new WorkItemLabel("legal/contracts", LabelPersistence.MANUAL, "alice"));
        assertThat(evaluator.evaluate(wi,
                ExpressionDescriptor.of("jq", ".labels | contains([\"legal/contracts\"])"))).isTrue();
    }

    private WorkItem wi(final WorkItemStatus s, final WorkItemPriority p, final String a) {
        final WorkItem wi = new WorkItem();
        wi.status = s;
        wi.priority = p;
        wi.assigneeId = a;
        return wi;
    }
}
