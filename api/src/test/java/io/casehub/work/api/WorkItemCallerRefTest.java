package io.casehub.work.api;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WorkItemCallerRefTest {

    private static final UUID CASE_UUID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    @Test
    void parseCaseId_validEngineFormat_returnsCaseId() {
        assertEquals(CASE_UUID, WorkItemCallerRef.parseCaseId(CASE_UUID + ":pi-001"));
    }

    @Test
    void parseCaseId_notAUuid_returnsNull() {
        assertNull(WorkItemCallerRef.parseCaseId("not-a-uuid:planItem"));
    }

    @Test
    void parseCaseId_noColon_returnsNull() {
        assertNull(WorkItemCallerRef.parseCaseId("justsomething"));
    }

    @Test
    void parseCaseId_null_returnsNull() {
        assertNull(WorkItemCallerRef.parseCaseId(null));
    }

    @Test
    void parseCaseId_emptyString_returnsNull() {
        assertNull(WorkItemCallerRef.parseCaseId(""));
    }
}
