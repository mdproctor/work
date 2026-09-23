package io.casehub.work.queues.api;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.platform.api.view.SubjectViewStore;
import io.casehub.platform.view.SubjectViewOrchestrator;
import io.casehub.work.queues.event.WorkItemQueueEvent;
import io.casehub.work.queues.repository.QueueSnapshotStore;
import io.casehub.work.queues.service.QueueMembershipService;
import io.casehub.work.queues.service.WorkItemQueueEventBroadcaster;
import io.casehub.work.rest.WorkItemMapper;
import io.casehub.work.rest.WorkItemResponse;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/queues")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class QueueResource {

    @Inject
    SubjectViewStore viewStore;

    @Inject
    SubjectViewOrchestrator orchestrator;

    @Inject
    WorkItemQueueEventBroadcaster queueEventBroadcaster;

    @Inject
    QueueMembershipService membershipService;

    @Inject
    QueueSnapshotStore snapshotStore;

    @Inject
    CurrentPrincipal currentPrincipal;

    public record CreateQueueRequest(String name, String labelPattern, String scope,
                                     String additionalConditions, String sortField, String sortDirection) {
    }

    @GET
    @Transactional
    public List<Map<String, Object>> list() {
        final Instant now = Instant.now();
        return viewStore.findByTenancy(currentPrincipal.tenancyId()).stream()
                        .map(q -> {
                            final var summary = membershipService.summarize(q, now);
                            return Map.<String, Object>of(
                                    "id", q.id(), "name", q.name(),
                                    "labelPattern", q.labelPattern(),
                                    "scope", q.scope() != null ? q.scope().value() : "/",
                                    "summary", summary);
                        })
                        .toList();
    }

    @GET
    @Path("/health")
    @Transactional
    public List<QueueHealthMetric> health() {
        final Instant now = Instant.now();
        final var queues = viewStore.findByTenancy(currentPrincipal.tenancyId());

        long total = 0;
        long pending = 0;
        long active = 0;
        long overdue = 0;
        long breached = 0;

        for (final var queue : queues) {
            final var summary = membershipService.summarize(queue, now);
            total += summary.total();
            pending += summary.byStatus().getOrDefault("PENDING", 0L);
            active += summary.byStatus().getOrDefault("IN_PROGRESS", 0L)
                    + summary.byStatus().getOrDefault("ASSIGNED", 0L);
            overdue += summary.overdue();
            breached += summary.claimDeadlineBreached();
        }

        return List.of(
                new QueueHealthMetric("total", total, "Total", "neutral"),
                new QueueHealthMetric("pending", pending, "Pending",
                        pending > 0 ? "warning" : "neutral"),
                new QueueHealthMetric("active", active, "Active", "neutral"),
                new QueueHealthMetric("overdue", overdue, "Overdue",
                        overdue > 0 ? "critical" : "neutral"),
                new QueueHealthMetric("breached", breached, "Claim SLA",
                        breached > 0 ? "critical" : "neutral"));
    }

    @POST
    @Transactional
    public Response create(final CreateQueueRequest req) {
        if (req.labelPattern() == null || req.labelPattern().isBlank()) {
            return Response.status(400).entity(Map.of("error", "labelPattern is required")).build();
        }
        final io.casehub.platform.api.path.Path scopePath;
        try {
            scopePath = (req.scope() == null || req.scope().isBlank())
                        ? io.casehub.platform.api.path.Path.root()
                        : io.casehub.platform.api.path.Path.parse(req.scope());
        } catch (IllegalArgumentException e) {
            return Response.status(400)
                           .entity(Map.of("error", "invalid scope: " + e.getMessage())).build();
        }
        var spec = new SubjectViewSpec(
                UUID.randomUUID(), req.name(), currentPrincipal.tenancyId(),
                req.labelPattern(), scopePath,
                req.sortField() != null ? req.sortField() : "createdAt",
                req.sortDirection() != null ? req.sortDirection() : "ASC",
                req.additionalConditions(), Instant.now());
        var saved = orchestrator.saveView(spec);
        return Response.status(201)
                       .entity(Map.of("id", saved.id(), "name", saved.name(),
                                      "labelPattern", saved.labelPattern()))
                       .build();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public Response query(@PathParam("id") final UUID id) {
        var spec = viewStore.findById(id).orElse(null);
        if (spec == null) {
            return Response.status(404).entity(Map.of("error", "Queue view not found")).build();
        }
        final var items = membershipService.evaluateMembers(spec).stream()
                                           .map(WorkItemMapper::toResponse)
                                           .sorted(buildComparator(spec.sortField(), spec.sortDirection()))
                                           .toList();
        return Response.ok(items).build();
    }

    private java.util.Comparator<WorkItemResponse> buildComparator(
            final String sortField, final String sortDirection) {
        final boolean asc = !"DESC".equalsIgnoreCase(sortDirection);
        final java.util.Comparator<WorkItemResponse> base = switch (sortField != null ? sortField : "createdAt") {
            case "priority" -> java.util.Comparator.comparing(
                    r -> r.priority() != null ? r.priority().name() : "");
            case "title" -> java.util.Comparator.comparing(
                    r -> r.title() != null ? r.title() : "");
            default -> // createdAt
                    java.util.Comparator.comparing(
                            r -> r.createdAt() != null ? r.createdAt() : java.time.Instant.EPOCH);
        };
        return asc ? base : base.reversed();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") final UUID id) {
        if (viewStore.findById(id).isEmpty()) {
            return Response.status(404).entity(Map.of("error", "Not found")).build();
        }
        orchestrator.deleteView(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/summary")
    @Transactional
    public Response summary(@PathParam("id") final UUID id) {
        var spec = viewStore.findById(id).orElse(null);
        if (spec == null) {
            return Response.status(404).entity(Map.of("error", "Queue view not found")).build();
        }
        return Response.ok(membershipService.summarize(spec, Instant.now())).build();
    }

    @GET
    @Path("/{id}/trend")
    @Transactional
    public Response trend(@PathParam("id") final UUID id,
                          @jakarta.ws.rs.QueryParam("period") final String periodParam) {
        var spec = viewStore.findById(id).orElse(null);
        if (spec == null) {
            return Response.status(404)
                           .entity(Map.of("error", "Queue view not found")).build();
        }
        final Duration period;
        try {
            period = parsePeriod(periodParam != null ? periodParam : "24h");
        } catch (final Exception e) {
            return Response.status(400)
                           .entity(Map.of("error", "Invalid period: " + periodParam)).build();
        }
        final Instant now       = Instant.now();
        final Instant from      = now.minus(period);
        final var     snapshots = snapshotStore.findByQueueAndPeriod(spec.id(), from, now);
        final var dataPoints = snapshots.stream()
                                        .map(s -> new QueueTrendResponse.DataPoint(s.snapshotAt, s.memberCount))
                                        .toList();
        return Response.ok(new QueueTrendResponse(
                spec.id(), spec.name(), period.toString(), dataPoints)).build();
    }

    static Duration parsePeriod(final String input) {
        if (input.startsWith("P") || input.startsWith("p")) {
            return Duration.parse(input);
        }
        final String normalized = input.toLowerCase();
        if (normalized.endsWith("h")) {
            return Duration.ofHours(
                    Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("d")) {
            return Duration.ofDays(
                    Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        return Duration.parse("PT" + input);
    }

    @GET
    @Path("/{id}/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<WorkItemQueueEvent> streamQueueEvents(@PathParam("id") final UUID queueViewId) {
        return queueEventBroadcaster.stream(queueViewId, currentPrincipal.tenancyId());
    }
}
