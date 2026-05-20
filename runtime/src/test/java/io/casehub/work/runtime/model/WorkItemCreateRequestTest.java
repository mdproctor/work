package io.casehub.work.runtime.model;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WorkItemCreateRequestTest {

    @Test
    void builder_setsAllFields() {
        final Instant now = Instant.now();
        final UUID templateId = UUID.randomUUID();

        final WorkItemCreateRequest req = WorkItemCreateRequest.builder()
                .title("Review contract")
                .description("desc")
                .category("legal")
                .formKey("form-1")
                .priority(WorkItemPriority.HIGH)
                .assigneeId("alice")
                .candidateGroups("lawyers")
                .candidateUsers("bob")
                .requiredCapabilities("legal-review")
                .createdBy("system")
                .payload("{}")
                .claimDeadline(now)
                .expiresAt(now)
                .followUpDate(now)
                .labels(List.of())
                .confidenceScore(0.9)
                .callerRef("ref-1")
                .claimDeadlineBusinessHours(8)
                .expiresAtBusinessHours(40)
                .templateId(templateId)
                .permittedOutcomes(List.of("approved", "rejected"))
                .inputDataSchema("{\"type\":\"object\"}")
                .outputDataSchema("{\"type\":\"object\"}")
                .excludedUsers("charlie")
                .build();

        assertEquals("Review contract", req.title);
        assertEquals("desc", req.description);
        assertEquals("legal", req.category);
        assertEquals("form-1", req.formKey);
        assertEquals(WorkItemPriority.HIGH, req.priority);
        assertEquals("alice", req.assigneeId);
        assertEquals("lawyers", req.candidateGroups);
        assertEquals("bob", req.candidateUsers);
        assertEquals("legal-review", req.requiredCapabilities);
        assertEquals("system", req.createdBy);
        assertEquals("{}", req.payload);
        assertEquals(now, req.claimDeadline);
        assertEquals(now, req.expiresAt);
        assertEquals(now, req.followUpDate);
        assertEquals(List.of(), req.labels);
        assertEquals(0.9, req.confidenceScore);
        assertEquals("ref-1", req.callerRef);
        assertEquals(8, req.claimDeadlineBusinessHours);
        assertEquals(40, req.expiresAtBusinessHours);
        assertEquals(templateId, req.templateId);
        assertEquals(List.of("approved", "rejected"), req.permittedOutcomes);
        assertEquals("{\"type\":\"object\"}", req.inputDataSchema);
        assertEquals("{\"type\":\"object\"}", req.outputDataSchema);
        assertEquals("charlie", req.excludedUsers);
    }

    @Test
    void builder_defaultsAllOptionalFieldsToNull() {
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder()
                .title("Minimal")
                .build();

        assertEquals("Minimal", req.title);
        assertNull(req.description);
        assertNull(req.category);
        assertNull(req.formKey);
        assertNull(req.priority);
        assertNull(req.assigneeId);
        assertNull(req.candidateGroups);
        assertNull(req.candidateUsers);
        assertNull(req.requiredCapabilities);
        assertNull(req.createdBy);
        assertNull(req.payload);
        assertNull(req.claimDeadline);
        assertNull(req.expiresAt);
        assertNull(req.followUpDate);
        assertNull(req.labels);
        assertNull(req.confidenceScore);
        assertNull(req.callerRef);
        assertNull(req.claimDeadlineBusinessHours);
        assertNull(req.expiresAtBusinessHours);
        assertNull(req.templateId);
        assertNull(req.permittedOutcomes);
        assertNull(req.inputDataSchema);
        assertNull(req.outputDataSchema);
        assertNull(req.excludedUsers);
    }

    @Test
    void build_rejectsNullTitle() {
        assertThrows(IllegalArgumentException.class,
                () -> WorkItemCreateRequest.builder().build());
    }

    @Test
    void build_rejectsBlankTitle() {
        assertThrows(IllegalArgumentException.class,
                () -> WorkItemCreateRequest.builder().title("").build());
        assertThrows(IllegalArgumentException.class,
                () -> WorkItemCreateRequest.builder().title("  ").build());
    }

    @Test
    void toBuilder_copiesAllFields() {
        final Instant now = Instant.now();
        final UUID templateId = UUID.randomUUID();

        final WorkItemCreateRequest original = WorkItemCreateRequest.builder()
                .title("original")
                .description("desc")
                .category("legal")
                .formKey("form-1")
                .priority(WorkItemPriority.HIGH)
                .assigneeId("alice")
                .candidateGroups("lawyers")
                .candidateUsers("bob")
                .requiredCapabilities("legal-review")
                .createdBy("system")
                .payload("{}")
                .claimDeadline(now)
                .expiresAt(now)
                .followUpDate(now)
                .labels(List.of())
                .confidenceScore(0.9)
                .callerRef("ref-1")
                .claimDeadlineBusinessHours(8)
                .expiresAtBusinessHours(40)
                .templateId(templateId)
                .permittedOutcomes(List.of("approved"))
                .inputDataSchema("{\"type\":\"object\"}")
                .outputDataSchema("{\"type\":\"object\"}")
                .excludedUsers("charlie")
                .build();

        final WorkItemCreateRequest copy = original.toBuilder().title("copy").build();

        assertEquals("copy", copy.title);
        assertEquals("desc", copy.description);
        assertEquals("legal", copy.category);
        assertEquals("form-1", copy.formKey);
        assertEquals(WorkItemPriority.HIGH, copy.priority);
        assertEquals("alice", copy.assigneeId);
        assertEquals("lawyers", copy.candidateGroups);
        assertEquals("bob", copy.candidateUsers);
        assertEquals("legal-review", copy.requiredCapabilities);
        assertEquals("system", copy.createdBy);
        assertEquals("{}", copy.payload);
        assertEquals(now, copy.claimDeadline);
        assertEquals(now, copy.expiresAt);
        assertEquals(now, copy.followUpDate);
        assertEquals(List.of(), copy.labels);
        assertEquals(0.9, copy.confidenceScore);
        assertEquals("ref-1", copy.callerRef);
        assertEquals(8, copy.claimDeadlineBusinessHours);
        assertEquals(40, copy.expiresAtBusinessHours);
        assertEquals(templateId, copy.templateId);
        assertEquals(List.of("approved"), copy.permittedOutcomes);
        assertEquals("{\"type\":\"object\"}", copy.inputDataSchema);
        assertEquals("{\"type\":\"object\"}", copy.outputDataSchema);
        assertEquals("charlie", copy.excludedUsers);
    }

    @Test
    void toBuilder_doesNotMutateOriginal() {
        final WorkItemCreateRequest original = WorkItemCreateRequest.builder()
                .title("original")
                .build();

        original.toBuilder().title("mutated").build();

        assertEquals("original", original.title);
    }

    @Test
    void equals_andHashCode_areConsistent() {
        final WorkItemCreateRequest a = WorkItemCreateRequest.builder().title("x").category("y").build();
        final WorkItemCreateRequest b = WorkItemCreateRequest.builder().title("x").category("y").build();
        final WorkItemCreateRequest c = WorkItemCreateRequest.builder().title("x").category("z").build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void builderHasSetterForEveryField() {
        final long fieldCount = Arrays.stream(WorkItemCreateRequest.class.getFields())
                .filter(f -> Modifier.isFinal(f.getModifiers()))
                .count();

        final long setterCount = Arrays.stream(WorkItemCreateRequest.Builder.class.getDeclaredMethods())
                .filter(m -> m.getReturnType() == WorkItemCreateRequest.Builder.class)
                .count();

        assertEquals(fieldCount, setterCount,
                "WorkItemCreateRequest.Builder is missing a setter — add one for every public final field");
    }
}
