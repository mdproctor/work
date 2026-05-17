package io.casehub.work.runtime.model;

import java.util.List;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.casehub.work.api.Outcome;

/**
 * JSON encoding and decoding utilities for outcome fields on {@link WorkItem} and
 * {@link WorkItemTemplate}.
 *
 * <p>
 * Pure static utilities — no CDI, no JPA. Safe to use from any layer (mapping,
 * service, event) without introducing layering violations.
 */
public final class OutcomeCodecs {

    private static final Logger LOG = Logger.getLogger(OutcomeCodecs.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OutcomeCodecs() {
    }

    /**
     * Parses the template's {@link WorkItemTemplate#outcomes} JSON into a list of
     * name strings for snapshotting onto {@link WorkItem#permittedOutcomes}.
     *
     * @param outcomesJson JSON of the form {@code [{"name":"approved","displayName":"..."},...]}
     * @return list of name strings, or null if outcomesJson is null/blank/empty/unparseable
     */
    public static List<String> parseOutcomeNames(final String outcomesJson) {
        if (outcomesJson == null || outcomesJson.isBlank()) {
            return null;
        }
        try {
            final List<Outcome> outcomes = MAPPER.readValue(outcomesJson, new TypeReference<>() {});
            if (outcomes.isEmpty()) {
                return null;
            }
            return outcomes.stream()
                    .map(Outcome::name)
                    .filter(n -> n != null && !n.isBlank())
                    .toList();
        } catch (final Exception e) {
            LOG.warnf("Failed to parse template outcomes JSON (treating as unconstrained): %s", e.getMessage());
            return null;
        }
    }

    /**
     * Encodes a list of {@link Outcome} objects to JSON for storage on
     * {@link WorkItemTemplate#outcomes}.
     *
     * @param outcomes list to encode; null or empty returns null
     * @return JSON string, or null
     */
    public static String encodeOutcomes(final List<Outcome> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(outcomes);
        } catch (final Exception e) {
            LOG.warnf("Failed to encode outcomes: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Encodes a list of outcome name strings to JSON for storage on
     * {@link WorkItem#permittedOutcomes}.
     *
     * @param names list of names; null or empty returns null
     * @return JSON string, or null
     */
    public static String encodePermittedOutcomes(final List<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(names);
        } catch (final Exception e) {
            LOG.warnf("Failed to encode permittedOutcomes: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Decodes {@link WorkItem#permittedOutcomes} JSON to a list of name strings.
     *
     * @param permittedOutcomesJson JSON array of strings; null or blank returns null
     * @return list of names, or null if unconstrained or unparseable
     */
    public static List<String> decodePermittedOutcomes(final String permittedOutcomesJson) {
        if (permittedOutcomesJson == null || permittedOutcomesJson.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(permittedOutcomesJson, new TypeReference<>() {});
        } catch (final Exception e) {
            LOG.warnf("Failed to decode permittedOutcomes JSON: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Decodes {@link WorkItemTemplate#outcomes} JSON to a list of {@link Outcome} objects.
     *
     * @param outcomesJson JSON string; null or blank returns empty list
     * @return list of outcomes, or empty list if unconstrained or unparseable
     */
    public static List<Outcome> decodeOutcomes(final String outcomesJson) {
        if (outcomesJson == null || outcomesJson.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(outcomesJson, new TypeReference<>() {});
        } catch (final Exception e) {
            LOG.warnf("Failed to decode outcomes JSON: %s", e.getMessage());
            return List.of();
        }
    }
}
