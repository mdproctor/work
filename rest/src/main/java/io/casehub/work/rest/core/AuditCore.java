package io.casehub.work.rest.core;

import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.AuditQuery;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuditCore {
    private final AuditEntryStore auditStore;

    public AuditCore(AuditEntryStore auditStore) {
        this.auditStore = auditStore;
    }

    public Map<String, Object> queryAudit(String actorId, String from, String to,
            String event, String type, int page, int size) {
        AuditQuery query = AuditQuery.builder()
                .actorId(actorId)
                .from(from != null ? Instant.parse(from) : null)
                .to(to != null ? Instant.parse(to) : null)
                .event(event)
                .type(type)
                .page(page)
                .size(size)
                .build();

        List<AuditEntry> entries = auditStore.query(query);
        long total = auditStore.count(query);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entries", entries.stream().map(this::toResponse).toList());
        result.put("page", query.page());
        result.put("size", query.size());
        result.put("total", total);
        return result;
    }

    private Map<String, Object> toResponse(AuditEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id);
        m.put("workItemId", e.workItemId);
        m.put("event", e.event);
        m.put("actor", e.actor);
        m.put("detail", e.detail);
        m.put("occurredAt", e.occurredAt);
        return m;
    }
}
