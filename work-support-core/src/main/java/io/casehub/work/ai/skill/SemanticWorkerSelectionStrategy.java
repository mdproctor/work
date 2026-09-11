package io.casehub.work.ai.skill;

import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.Capability;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.SkillProfile;
import io.casehub.work.api.spi.SkillProfileProvider;
import io.casehub.work.api.WorkerCandidate;
import io.casehub.work.api.spi.SkillMatcher;
import io.casehub.work.api.spi.WorkerSelectionStrategy;
import io.casehub.work.core.strategy.LeastLoadedStrategy;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SemanticWorkerSelectionStrategy implements WorkerSelectionStrategy {

    @Override
    public String id() { return "semantic"; }

    private static final Logger LOG = Logger.getLogger(SemanticWorkerSelectionStrategy.class.getName());

    private final SkillProfileProvider profileProvider;
    private final SkillMatcher matcher;
    private final LeastLoadedStrategy fallback;
    private final boolean enabled;
    private final double scoreThreshold;

    public SemanticWorkerSelectionStrategy(
            final SkillProfileProvider profileProvider,
            final SkillMatcher matcher,
            final LeastLoadedStrategy fallback,
            final boolean enabled,
            final double scoreThreshold) {
        this.profileProvider = profileProvider;
        this.matcher = matcher;
        this.fallback = fallback;
        this.enabled = enabled;
        this.scoreThreshold = scoreThreshold;
    }

    @Override
    public AssignmentDecision select(final SelectionContext context,
            final List<WorkerCandidate> candidates) {
        if (!enabled || candidates.isEmpty()) {
            return fallback.select(context, candidates);
        }
        try {
            return candidates.stream()
                    .map(c -> {
                        final Set<String> capabilityIds = c.capabilities().stream()
                                .map(Capability::id).collect(Collectors.toSet());
                        final SkillProfile profile = profileProvider.getProfile(
                                c.id(), capabilityIds);
                        final double score = matcher.score(profile, context);
                        return new CandidateScore(c, score);
                    })
                    .filter(cs -> cs.score > scoreThreshold)
                    .max(Comparator.comparingDouble(cs -> cs.score))
                    .map(cs -> AssignmentDecision.assignTo(cs.candidate.id()))
                    .orElseGet(() -> {
                        LOG.warning(String.format(
                                "SemanticWorkerSelectionStrategy: no candidate scored above "
                                + "threshold %.2f — falling back to LeastLoadedStrategy",
                                scoreThreshold));
                        return fallback.select(context, candidates);
                    });
        } catch (final Exception e) {
            LOG.warning("SemanticWorkerSelectionStrategy failed: " + e.getMessage()
                    + " — falling back to LeastLoadedStrategy");
            return fallback.select(context, candidates);
        }
    }

    private record CandidateScore(WorkerCandidate candidate, double score) {
    }
}
