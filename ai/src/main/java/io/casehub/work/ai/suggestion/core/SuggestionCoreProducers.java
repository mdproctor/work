package io.casehub.work.ai.suggestion.core;

import io.casehub.work.ai.suggestion.ResolutionSuggestionService;
import io.casehub.work.api.spi.WorkItemStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class SuggestionCoreProducers {

    @Produces
    @ApplicationScoped
    public ResolutionSuggestionCore resolutionSuggestionCore(WorkItemStore workItemStore,
            ResolutionSuggestionService suggestionService) {
        return new ResolutionSuggestionCore(workItemStore, suggestionService);
    }
}
