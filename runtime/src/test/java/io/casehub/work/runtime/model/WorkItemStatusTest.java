package io.casehub.work.runtime.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkItemStatusTest {

    @Test
    void expired_isTerminal() {
        assertThat(WorkItemStatus.EXPIRED.isTerminal()).isTrue();
    }

    @Test
    void delegated_isNotTerminal() {
        assertThat(WorkItemStatus.DELEGATED.isTerminal()).isFalse();
    }

    @Test
    void completed_isTerminal() {
        assertThat(WorkItemStatus.COMPLETED.isTerminal()).isTrue();
    }

    @Test
    void rejected_isTerminal() {
        assertThat(WorkItemStatus.REJECTED.isTerminal()).isTrue();
    }

    @Test
    void cancelled_isTerminal() {
        assertThat(WorkItemStatus.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    void escalated_isTerminal() {
        assertThat(WorkItemStatus.ESCALATED.isTerminal()).isTrue();
    }

    @Test
    void pending_isActive() {
        assertThat(WorkItemStatus.PENDING.isActive()).isTrue();
    }

    @Test
    void assigned_isActive() {
        assertThat(WorkItemStatus.ASSIGNED.isActive()).isTrue();
    }

    @Test
    void inProgress_isActive() {
        assertThat(WorkItemStatus.IN_PROGRESS.isActive()).isTrue();
    }

    @Test
    void suspended_isActive() {
        assertThat(WorkItemStatus.SUSPENDED.isActive()).isTrue();
    }

    @Test
    void expired_isNotActive() {
        assertThat(WorkItemStatus.EXPIRED.isActive()).isFalse();
    }

    @Test
    void completed_isNotActive() {
        assertThat(WorkItemStatus.COMPLETED.isActive()).isFalse();
    }

    @Test
    void delegated_isActive() {
        assertThat(WorkItemStatus.DELEGATED.isActive()).isTrue();
    }

    @Test
    void terminalStatusesAreNeverActive() {
        for (final WorkItemStatus s : WorkItemStatus.values()) {
            if (s.isTerminal()) {
                assertThat(s.isActive())
                        .as("%s is both terminal and active — must be one or the other", s)
                        .isFalse();
            }
        }
    }
}
