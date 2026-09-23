package io.casehub.work.rest.core;

import java.util.List;
import java.util.Map;

import jakarta.transaction.Transactional;

import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.LabelDefinition;
import io.casehub.work.runtime.model.LabelVocabulary;
import io.casehub.work.runtime.service.LabelVocabularyService;

public class VocabularyCore {

    private final LabelVocabularyService vocabularyService;

    public VocabularyCore(LabelVocabularyService vocabularyService) {
        this.vocabularyService = vocabularyService;
    }

    public List<Map<String, Object>> listAll() {
        return vocabularyService.listAllDefinitions().stream()
                .map(sd -> Map.<String, Object>of(
                        "id", sd.definition().id,
                        "path", sd.definition().path.value(),
                        "vocabularyId", sd.definition().vocabularyId,
                        "scope", sd.scope().value(),
                        "description", sd.definition().description != null ? sd.definition().description : "",
                        "createdBy", sd.definition().createdBy,
                        "createdAt", sd.definition().createdAt))
                .toList();
    }

    @Transactional
    public AddDefinitionResult addDefinition(AddDefinitionRequest request) {
        if (request == null || request.path() == null || request.path().isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        if (request.path().contains("*") || request.path().contains("?")) {
            throw new IllegalArgumentException("path must not contain wildcard characters");
        }

        Path labelPath;
        try {
            labelPath = Path.parse(request.path());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid path: " + e.getMessage());
        }

        Path scopePath;
        try {
            scopePath = (request.scope() == null || request.scope().isBlank())
                    ? Path.root()
                    : Path.parse(request.scope());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid scope: " + e.getMessage());
        }

        String vocabName = scopePath.value().isEmpty() ? "Global" : scopePath.value();
        LabelVocabulary vocab = vocabularyService.findOrCreateVocabulary(scopePath, vocabName);

        LabelDefinition def = vocabularyService.addDefinition(
                vocab.id, labelPath, request.description(),
                request.addedBy() != null ? request.addedBy() : "unknown");

        return new AddDefinitionResult(def.id, def.path.value(), scopePath.value());
    }
}
