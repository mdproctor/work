package io.casehub.work.ai.suggestion;

import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import dev.langchain4j.model.chat.ChatModel;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.spi.WorkItemStore;

public class ResolutionSuggestionService {

    private static final Logger LOG = Logger.getLogger(ResolutionSuggestionService.class.getName());

    private final WorkItemStore workItemStore;
    private final ChatModel chatModel;
    private final int historyLimit;

    public ResolutionSuggestionService(
            final WorkItemStore workItemStore,
            final ChatModel chatModel,
            final int historyLimit) {
        this.workItemStore = workItemStore;
        this.chatModel = chatModel;
        this.historyLimit = historyLimit;
    }

    public boolean isModelAvailable() {
        return chatModel != null;
    }

    public String suggest(final WorkItem workItem) {
        if (chatModel == null) {
            return null;
        }
        final List<WorkItem> examples = findExamples(workItem);
        if (examples.isEmpty()) {
            LOG.fine("No completed examples found for WorkItem " + workItem.id() + " — skipping suggestion");
            return null;
        }
        try {
            final String prompt = buildPrompt(workItem, examples);
            return chatModel.chat(prompt);
        } catch (final Exception e) {
            LOG.warning("ChatModel call failed for WorkItem " + workItem.id() + ": " + e.getMessage());
            return null;
        }
    }

    public int exampleCount(final WorkItem workItem) {
        return findExamples(workItem).size();
    }

    private List<WorkItem> findExamples(final WorkItem workItem) {
        if (!workItem.types().isEmpty()) {
            final String primaryType = workItem.types().iterator().next();
            final List<WorkItem> byType = completedWithResolution(primaryType);
            if (!byType.isEmpty()) {
                return byType;
            }
        }
        return completedWithResolution(null);
    }

    private List<WorkItem> completedWithResolution(final String type) {
        final WorkItemQuery query = WorkItemQuery.builder()
                .status(WorkItemStatus.COMPLETED)
                .type(type)
                .build();
        return workItemStore.scan(query).stream()
                .filter(wi -> wi.resolution() != null && !wi.resolution().isBlank())
                .filter(wi -> wi.completedAt() != null)
                .sorted(Comparator.comparing((WorkItem wi) -> wi.completedAt()).reversed())
                .limit(historyLimit)
                .collect(Collectors.toList());
    }

    private String buildPrompt(final WorkItem current, final List<WorkItem> examples) {
        final StringBuilder sb = new StringBuilder();
        sb.append("You are a work item resolution assistant.\n\n");

        if (!current.types().isEmpty()) {
            sb.append("Types: ").append(current.types().stream()
                    .collect(Collectors.joining(", "))).append("\n\n");
        }

        sb.append("The following are past resolutions for similar work items:\n\n");
        IntStream.range(0, examples.size()).forEach(i -> {
            final WorkItem ex = examples.get(i);
            sb.append("Example ").append(i + 1).append(":\n");
            sb.append("Title: ").append(ex.title()).append("\n");
            if (ex.description() != null && !ex.description().isBlank()) {
                sb.append("Description: ").append(ex.description()).append("\n");
            }
            sb.append("Resolution: ").append(ex.resolution()).append("\n\n");
        });

        sb.append("Current work item:\n");
        sb.append("Title: ").append(current.title()).append("\n");
        if (current.description() != null && !current.description().isBlank()) {
            sb.append("Description: ").append(current.description()).append("\n");
        }
        if (current.payload() != null && !current.payload().isBlank()) {
            sb.append("Payload: ").append(current.payload()).append("\n");
        }

        sb.append("\nBased on the examples above, suggest a resolution for this work item. ")
                .append("Respond with only valid JSON matching the structure of the example resolutions. ")
                .append("Do not include any explanation outside the JSON.");

        return sb.toString();
    }
}
