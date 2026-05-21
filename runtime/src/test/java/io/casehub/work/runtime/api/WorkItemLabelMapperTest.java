package io.casehub.work.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.LabelPersistence;
import io.casehub.work.runtime.model.WorkItemCreateRequest;
import io.casehub.work.runtime.model.WorkItemLabelRequest;

class WorkItemLabelMapperTest {

    @Test
    void toServiceRequest_passesLabelsThroughUnchanged() {
        final var label = new WorkItemLabelRequest("legal/contracts", LabelPersistence.MANUAL, "alice");
        final var httpReq = CreateWorkItemRequest.builder()
                .title("Test")
                .labels(List.of(label))
                .build();

        final var serviceReq = WorkItemMapper.toServiceRequest(httpReq);

        assertThat(serviceReq.labels).hasSize(1);
        assertThat(serviceReq.labels.get(0).path()).isEqualTo("legal/contracts");
        assertThat(serviceReq.labels.get(0).persistence()).isEqualTo(LabelPersistence.MANUAL);
        assertThat(serviceReq.labels.get(0).appliedBy()).isEqualTo("alice");
    }

    @Test
    void toServiceRequest_nullLabels_remainsNull() {
        final var httpReq = CreateWorkItemRequest.builder().title("Test").build();
        final var serviceReq = WorkItemMapper.toServiceRequest(httpReq);
        assertThat(serviceReq.labels).isNull();
    }
}
