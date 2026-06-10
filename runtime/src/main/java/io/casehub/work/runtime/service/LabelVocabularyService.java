package io.casehub.work.runtime.service;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.LabelDefinition;
import io.casehub.work.runtime.model.LabelVocabulary;
import io.casehub.work.runtime.model.VocabularyScope;
import io.casehub.work.runtime.repository.LabelDefinitionStore;
import io.casehub.work.runtime.repository.LabelVocabularyStore;

@ApplicationScoped
public class LabelVocabularyService {

    @Inject
    LabelDefinitionStore labelDefinitionStore;

    @Inject
    LabelVocabularyStore labelVocabularyStore;

    /**
     * Returns true if a label declared in {@code definitionScope} is accessible
     * to a caller operating at {@code callerScope}.
     *
     * <p>
     * Accessibility: the definition scope must be at the same level or broader
     * (lower ordinal = broader scope).
     */
    public static boolean isScopeAccessible(final VocabularyScope definitionScope,
            final VocabularyScope callerScope) {
        return definitionScope.ordinal() <= callerScope.ordinal();
    }

    /**
     * Returns true if the given label {@code path} matches the {@code pattern}.
     *
     * <ul>
     * <li>Exact: {@code "legal"} matches only {@code "legal"}</li>
     * <li>Single wildcard {@code "legal/*"}: one segment below, not multiple</li>
     * <li>Multi wildcard {@code "legal/**"}: any path below {@code "legal/"}</li>
     * </ul>
     */
    public static boolean matchesPattern(final String pattern, final String path) {
        if (pattern.endsWith("/**")) {
            final String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix + "/");
        }
        if (pattern.endsWith("/*")) {
            final String prefix = pattern.substring(0, pattern.length() - 2);
            if (!path.startsWith(prefix + "/")) {
                return false;
            }
            final String remainder = path.substring(prefix.length() + 1);
            return !remainder.contains("/");
        }
        return pattern.equals(path);
    }

    /**
     * Returns true if the given path is declared in any accessible vocabulary.
     * Scope enforcement is deferred — any declared path is currently accepted.
     */
    @Transactional
    public boolean isDeclared(final Path path) {
        return !labelDefinitionStore.findByPath(path).isEmpty();
    }

    /**
     * Add a new label definition to the given vocabulary.
     */
    @Transactional
    public LabelDefinition addDefinition(final UUID vocabularyId, final Path path,
            final String description, final String createdBy) {
        final LabelDefinition def = new LabelDefinition();
        def.path = path;
        def.vocabularyId = vocabularyId;
        def.description = description;
        def.createdBy = createdBy;
        return labelDefinitionStore.put(def);
    }

    /**
     * List all label definitions accessible at the given scope (at or above).
     */
    @Transactional
    public List<LabelDefinition> listAccessible(final VocabularyScope callerScope) {
        return labelVocabularyStore.scanAll().stream()
                .filter(v -> isScopeAccessible(v.scope, callerScope))
                .flatMap(v -> labelDefinitionStore.findByVocabularyId(v.id).stream())
                .toList();
    }

    /**
     * Find the GLOBAL vocabulary (always present — seeded by Flyway V3).
     */
    @Transactional
    public LabelVocabulary findGlobalVocabulary() {
        return labelVocabularyStore.scanAll().stream()
                .filter(v -> v.scope == VocabularyScope.GLOBAL)
                .findFirst()
                .orElse(null);
    }

    /**
     * Find an existing vocabulary at the given scope and ownerId, or create one.
     * GLOBAL scope: use {@link #findGlobalVocabulary()} instead.
     */
    @Transactional
    public LabelVocabulary findOrCreateVocabulary(final VocabularyScope scope, final String ownerId,
            final String name) {
        LabelVocabulary existing = labelVocabularyStore.scanAll().stream()
                .filter(v -> v.scope == scope && ownerId.equals(v.ownerId))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        final LabelVocabulary vocab = new LabelVocabulary();
        vocab.scope = scope;
        vocab.ownerId = ownerId;
        vocab.name = name;
        return labelVocabularyStore.put(vocab);
    }
}
