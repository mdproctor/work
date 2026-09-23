package io.casehub.work.reports.api.core;

import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.reports.service.ActorReport;
import io.casehub.work.reports.service.QueueHealthReport;
import io.casehub.work.reports.service.ReportService;
import io.casehub.work.reports.service.SlaBreachReport;
import io.casehub.work.reports.service.ThroughputReport;
import java.time.Instant;

public class ReportCore {
    private final ReportService reportService;

    public ReportCore(ReportService reportService) {
        this.reportService = reportService;
    }

    public SlaBreachReport slaBreaches(String from, String to, String type, String priority) {
        return reportService.slaBreaches(parseInstant(from), parseInstant(to), type, parsePriority(priority));
    }

    public ActorReport actorPerformance(String actorId, String from, String to, String type) {
        return reportService.actorPerformance(actorId, parseInstant(from), parseInstant(to), type);
    }

    public ThroughputReport throughput(String from, String to, String groupBy) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("'from' and 'to' are required for throughput reports");
        }
        if (!groupBy.equals("day") && !groupBy.equals("week") && !groupBy.equals("month")) {
            throw new IllegalArgumentException("Invalid groupBy '" + groupBy + "': must be day, week, or month");
        }
        return reportService.throughput(parseInstant(from), parseInstant(to), groupBy);
    }

    public QueueHealthReport queueHealth(String type, String priority) {
        return reportService.queueHealth(type, parsePriority(priority));
    }

    private static WorkItemPriority parsePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return null;
        }
        try {
            return WorkItemPriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid priority: " + priority);
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null) {
            return null;
        }
        return Instant.parse(value);
    }
}
