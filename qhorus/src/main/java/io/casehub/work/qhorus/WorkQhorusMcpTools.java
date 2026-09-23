package io.casehub.work.qhorus;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemRef;
import io.casehub.work.api.spi.WorkItemCreator;
import io.quarkiverse.mcp.server.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
public class WorkQhorusMcpTools {

    private static final Logger LOG = Logger.getLogger(WorkQhorusMcpTools.class);

    @Inject ChannelReader channelReader;
    @Inject MessageDispatcher messageDispatcher;
    @Inject WorkItemCreator workItemCreator;
    @Inject CurrentPrincipal currentPrincipal;

    @Tool(description = "Request human work by creating a WorkItem and posting a QUERY to a Qhorus channel")
    public HumanWorkResponse requestHumanWork(
            String channel, String title, String description,
            String candidateGroups, String priority, String payload,
            String templateId, String sender) {

        final Channel ch = channelReader.findByName(channel)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channel));

        final String correlationId = UUID.randomUUID().toString();

        final DispatchResult queryResult = messageDispatcher.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender(sender)
                .type(MessageType.QUERY)
                .content(title + (description != null ? " — " + description : ""))
                .correlationId(correlationId)
                .actorType(ActorType.AGENT)
                .tenancyId(currentPrincipal.tenancyId())
                .build());

        final String callerRef = new QhorusRef(ch.id(), queryResult.messageId(), correlationId).encode();

        final WorkItemCreateRequest.Builder requestBuilder = WorkItemCreateRequest.builder()
                .title(title)
                .description(description)
                .callerRef(callerRef)
                .createdBy("qhorus:" + sender)
                .tenancyId(currentPrincipal.tenancyId());

        if (candidateGroups != null) requestBuilder.candidateGroups(candidateGroups);
        if (priority != null) requestBuilder.priority(WorkItemPriority.valueOf(priority.toUpperCase()));
        if (payload != null) requestBuilder.payload(payload);
        if (templateId != null) requestBuilder.templateId(UUID.fromString(templateId));

        final WorkItemRef ref = workItemCreator.create(requestBuilder.build());

        return new HumanWorkResponse(ref.id(), callerRef, correlationId, ref.status().name());
    }

    @Tool(description = "Check the current status of a previously requested human work item")
    public WorkStatusResponse checkWorkStatus(String callerRef) {
        return workItemCreator.findByCallerRef(callerRef)
                .map(ref -> new WorkStatusResponse(ref.id(), ref.status().name(),
                        ref.assigneeId(), ref.outcome(), ref.resolution(), false))
                .orElse(new WorkStatusResponse(null, "NOT_FOUND", null, null, null, false));
    }

    @Tool(description = "Poll until a human work item reaches a terminal state or times out")
    public WorkStatusResponse waitForWork(String callerRef, int timeoutSeconds, int pollIntervalSeconds) {
        final int timeout = timeoutSeconds > 0 ? timeoutSeconds : 300;
        final int interval = pollIntervalSeconds > 0 ? pollIntervalSeconds : 5;
        final long deadline = System.currentTimeMillis() + (timeout * 1000L);

        while (System.currentTimeMillis() < deadline) {
            final var refOpt = workItemCreator.findByCallerRef(callerRef);
            if (refOpt.isEmpty()) {
                return new WorkStatusResponse(null, "NOT_FOUND", null, null, null, false);
            }
            final WorkItemRef ref = refOpt.get();
            if (ref.status().isTerminal()) {
                return new WorkStatusResponse(ref.id(), ref.status().name(),
                        ref.assigneeId(), ref.outcome(), ref.resolution(), false);
            }
            try {
                Thread.sleep(interval * 1000L);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return new WorkStatusResponse(ref.id(), ref.status().name(),
                        ref.assigneeId(), ref.outcome(), ref.resolution(), true);
            }
        }

        return workItemCreator.findByCallerRef(callerRef)
                .map(ref -> new WorkStatusResponse(ref.id(), ref.status().name(),
                        ref.assigneeId(), ref.outcome(), ref.resolution(), true))
                .orElse(new WorkStatusResponse(null, "NOT_FOUND", null, null, null, true));
    }

    public record HumanWorkResponse(UUID workItemId, String callerRef, String correlationId, String status) {}
    public record WorkStatusResponse(UUID workItemId, String status, String assigneeId,
                                     String outcome, String resolution, boolean timedOut) {}
}
