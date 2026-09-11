package io.casehub.work.ai.skill;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.casehub.platform.api.util.Vectors;
import io.casehub.work.api.Capability;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.SkillProfile;
import io.casehub.work.api.spi.SkillMatcher;

import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EmbeddingSkillMatcher implements SkillMatcher {

    private static final Logger LOG = Logger.getLogger(EmbeddingSkillMatcher.class.getName());

    private final EmbeddingModel embeddingModel;

    public EmbeddingSkillMatcher(final EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public double score(final SkillProfile workerProfile, final SelectionContext context) {
        if (embeddingModel == null) {
            LOG.warning("No EmbeddingModel available — returning -1.0. "
                     + "Configure a langchain4j provider to enable semantic matching.");
            return -1.0;
        }
        try {
            final float[] workerVec = embeddingModel
                                              .embed(workerProfile.narrative() != null ? workerProfile.narrative() : "")
                                              .content().vector();
            final float[] requirementVec = embeddingModel
                                                   .embed(requirementText(context))
                                                   .content().vector();
            return Vectors.cosineSimilarity(workerVec, requirementVec);
        } catch (final Exception e) {
            LOG.warning("EmbeddingModel failed — returning -1.0 for candidate scoring: " + e.getMessage());
            return -1.0;
        }
    }

    private String requirementText(final SelectionContext ctx) {
        final String capabilitiesText = ctx.requiredCapabilities() == null || ctx.requiredCapabilities().isEmpty()
                ? null
                : ctx.requiredCapabilities().stream().map(Capability::id).collect(Collectors.joining(" "));
        final String typesText = ctx.types() == null || ctx.types().isEmpty()
                ? null
                : String.join(" ", ctx.types());
        return Stream.of(ctx.title(), ctx.description(), capabilitiesText, typesText)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
    }
}
