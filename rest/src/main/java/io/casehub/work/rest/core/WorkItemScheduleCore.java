package io.casehub.work.rest.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.transaction.Transactional;

import io.casehub.work.runtime.model.WorkItemSchedule;
import io.casehub.work.runtime.repository.WorkItemScheduleStore;
import io.casehub.work.runtime.service.WorkItemScheduleService;

public class WorkItemScheduleCore {

    private final WorkItemScheduleService scheduleService;
    private final WorkItemScheduleStore scheduleStore;

    public WorkItemScheduleCore(WorkItemScheduleService scheduleService,
            WorkItemScheduleStore scheduleStore) {
        this.scheduleService = scheduleService;
        this.scheduleStore = scheduleStore;
    }

    @Transactional
    public Map<String, Object> create(CreateScheduleRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (request.templateId() == null || request.templateId().isBlank()) {
            throw new IllegalArgumentException("templateId is required");
        }
        if (request.cronExpression() == null || request.cronExpression().isBlank()) {
            throw new IllegalArgumentException("cronExpression is required");
        }
        try {
            WorkItemSchedule s = scheduleService.create(
                    request.name(),
                    UUID.fromString(request.templateId()),
                    request.cronExpression(),
                    request.createdBy() != null ? request.createdBy() : "unknown");
            return toResponse(s);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("cron")) {
                throw new IllegalArgumentException("Invalid cron expression: " + e.getMessage(), e);
            }
            throw new IllegalArgumentException("Invalid cron expression — use Quartz format, e.g. '0 0 9 * * ?'", e);
        }
    }

    public List<Map<String, Object>> list() {
        return scheduleStore.scanAll().stream().map(this::toResponse).toList();
    }

    public Optional<Map<String, Object>> get(UUID id) {
        return scheduleService.findById(id).map(this::toResponse);
    }

    @Transactional
    public boolean delete(UUID id) {
        return scheduleStore.delete(id);
    }

    @Transactional
    public Optional<Map<String, Object>> setActive(UUID id, SetActiveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("body required: {\"active\": true|false}");
        }
        try {
            return scheduleService.setActive(id, request.active()).map(this::toResponse);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not recompute nextFireAt: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> toResponse(WorkItemSchedule s) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id);
        m.put("name", s.name);
        m.put("templateId", s.templateId);
        m.put("cronExpression", s.cronExpression);
        m.put("active", s.active);
        m.put("createdBy", s.createdBy);
        m.put("createdAt", s.createdAt);
        m.put("lastFiredAt", s.lastFiredAt);
        m.put("nextFireAt", s.nextFireAt);
        return m;
    }
}
