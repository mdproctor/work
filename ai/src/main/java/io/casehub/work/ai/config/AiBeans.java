package io.casehub.work.ai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.casehub.work.ai.skill.CapabilitiesSkillProfileProvider;
import io.casehub.work.ai.skill.CompositeSkillProfileProvider;
import io.casehub.work.ai.skill.EmbeddingSkillMatcher;
import io.casehub.work.ai.skill.ResolutionHistorySkillProfileProvider;
import io.casehub.work.ai.skill.SemanticWorkerSelectionStrategy;
import io.casehub.work.ai.suggestion.ResolutionSuggestionService;
import io.casehub.work.api.spi.SkillMatcher;
import io.casehub.work.api.spi.SkillProfileProvider;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.core.strategy.LeastLoadedStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class AiBeans {

    @Produces
    @Alternative
    public CapabilitiesSkillProfileProvider capabilitiesSkillProfileProvider() {
        return new CapabilitiesSkillProfileProvider();
    }

    @Produces
    @Alternative
    public ResolutionHistorySkillProfileProvider resolutionHistorySkillProfileProvider(
            final WorkItemStore workItemStore,
            final WorkItemsAiConfig config) {
        return new ResolutionHistorySkillProfileProvider(workItemStore, config.semantic().historyLimit());
    }

    @Produces
    public EmbeddingSkillMatcher embeddingSkillMatcher(final Instance<EmbeddingModel> embeddingModel) {
        return new EmbeddingSkillMatcher(embeddingModel.isResolvable()
                ? embeddingModel.get()
                : null);
    }

    @Produces
    @Alternative
    @jakarta.annotation.Priority(2)
    public CompositeSkillProfileProvider compositeSkillProfileProvider(
            final Instance<SkillProfileProvider> allProviders) {
        return new CompositeSkillProfileProvider(
                allProviders.stream().collect(Collectors.toList()));
    }

    @Produces
    public SemanticWorkerSelectionStrategy semanticWorkerSelectionStrategy(
            final SkillProfileProvider profileProvider,
            final SkillMatcher matcher,
            final LeastLoadedStrategy fallback,
            final WorkItemsAiConfig config) {
        return new SemanticWorkerSelectionStrategy(
                profileProvider, matcher, fallback,
                config.semantic().enabled(), config.semantic().scoreThreshold());
    }

    @Produces
    public ResolutionSuggestionService resolutionSuggestionService(
            final WorkItemStore workItemStore,
            final Instance<ChatModel> chatModel,
            final WorkItemsAiConfig config) {
        return new ResolutionSuggestionService(
                workItemStore,
                chatModel.isResolvable() ? chatModel.get() : null,
                config.suggestion().historyLimit());
    }
}
