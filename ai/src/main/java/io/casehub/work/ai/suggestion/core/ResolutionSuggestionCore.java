package io.casehub.work.ai.suggestion.core;

import java.util.Optional;
import java.util.UUID;

import io.casehub.work.ai.suggestion.ResolutionSuggestionResponse;
import io.casehub.work.ai.suggestion.ResolutionSuggestionService;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.spi.WorkItemStore;

public class ResolutionSuggestionCore {

    private final WorkItemStore workItemStore;
    private final ResolutionSuggestionService suggestionService;

    public ResolutionSuggestionCore(WorkItemStore workItemStore,
            ResolutionSuggestionService suggestionService) {
        this.workItemStore = workItemStore;
        this.suggestionService = suggestionService;
    }

    public Optional<ResolutionSuggestionResponse> suggest(UUID id) {
        WorkItem workItem = workItemStore.get(id).orElse(null);
        if (workItem == null) {
            return Optional.empty();
        }

        if (!suggestionService.isModelAvailable()) {
            return Optional.of(ResolutionSuggestionResponse.noModel(id));
        }

        int exampleCount = suggestionService.exampleCount(workItem);
        String suggestion = suggestionService.suggest(workItem);

        if (suggestion == null) {
            return Optional.of(ResolutionSuggestionResponse.noSuggestion(id, exampleCount));
        }

        return Optional.of(new ResolutionSuggestionResponse(id, suggestion, exampleCount, true));
    }
}
