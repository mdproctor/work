package io.casehub.work.runtime.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkItemLabelTest {

    @Test
    void labelPersistence_hasTwoValues() {
        assertThat(io.casehub.work.api.LabelPersistence.values())
                .containsExactlyInAnyOrder(io.casehub.work.api.LabelPersistence.MANUAL, io.casehub.work.api.LabelPersistence.INFERRED);
    }

    @Test
    void workItemLabel_manualConstructor_setsAllFields() {
        var label = new WorkItemLabel("legal/contracts", io.casehub.work.api.LabelPersistence.MANUAL, "alice");
        assertThat(label.path).isEqualTo("legal/contracts");
        assertThat(label.persistence).isEqualTo(io.casehub.work.api.LabelPersistence.MANUAL);
        assertThat(label.appliedBy).isEqualTo("alice");
    }

    @Test
    void workItemLabel_inferredConstructor_nullAppliedBy() {
        var label = new WorkItemLabel("intake", io.casehub.work.api.LabelPersistence.INFERRED, null);
        assertThat(label.path).isEqualTo("intake");
        assertThat(label.persistence).isEqualTo(io.casehub.work.api.LabelPersistence.INFERRED);
        assertThat(label.appliedBy).isNull();
    }

    @Test
    void workItemLabel_singleSegmentPath_isValid() {
        var label = new WorkItemLabel("legal", io.casehub.work.api.LabelPersistence.MANUAL, "bob");
        assertThat(label.path).isEqualTo("legal");
    }

    @Test
    void workItemLabel_equalsAndHashCode_sameFieldsAreEqual() {
        var a = new WorkItemLabel("legal/contracts", io.casehub.work.api.LabelPersistence.MANUAL, "alice");
        var b = new WorkItemLabel("legal/contracts", io.casehub.work.api.LabelPersistence.MANUAL, "alice");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void workItemLabel_equalsAndHashCode_differentPathNotEqual() {
        var a = new WorkItemLabel("legal/contracts", io.casehub.work.api.LabelPersistence.MANUAL, "alice");
        var b = new WorkItemLabel("legal/ip", io.casehub.work.api.LabelPersistence.MANUAL, "alice");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void workItemLabel_equalsAndHashCode_differentPersistenceNotEqual() {
        var a = new WorkItemLabel("legal", io.casehub.work.api.LabelPersistence.MANUAL, "alice");
        var b = new WorkItemLabel("legal", io.casehub.work.api.LabelPersistence.INFERRED, null);
        assertThat(a).isNotEqualTo(b);
    }
}
