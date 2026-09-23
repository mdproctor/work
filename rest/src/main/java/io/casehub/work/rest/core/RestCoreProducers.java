package io.casehub.work.rest.core;

import java.util.ArrayList;
import java.util.List;

import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.platform.api.label.LabelRule;
import io.casehub.work.api.spi.WorkItemOperations;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.runtime.filter.LabelRuleEngine;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.LabelRuleStore;
import io.casehub.work.runtime.repository.WorkItemRelationStore;
import io.casehub.work.runtime.repository.WorkItemScheduleStore;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;
import io.casehub.work.runtime.repository.WorkItemTemplateStore;
import io.casehub.work.runtime.service.LabelVocabularyService;
import io.casehub.work.runtime.service.WorkItemScheduleService;
import io.casehub.work.runtime.service.WorkItemSpawnService;
import io.casehub.work.runtime.service.WorkItemTemplateService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class RestCoreProducers {

    @Produces
    @ApplicationScoped
    public AuditCore auditCore(AuditEntryStore auditStore) {
        return new AuditCore(auditStore);
    }

    @Produces
    @ApplicationScoped
    public WorkItemBulkCore workItemBulkCore(WorkItemOperations workItemService) {
        return new WorkItemBulkCore(workItemService);
    }

    @Produces
    @ApplicationScoped
    public VocabularyCore vocabularyCore(LabelVocabularyService vocabularyService) {
        return new VocabularyCore(vocabularyService);
    }

    @Produces
    @ApplicationScoped
    public SpawnGroupCore spawnGroupCore(WorkItemSpawnGroupStore spawnGroupStore,
            WorkItemRelationStore relationStore) {
        return new SpawnGroupCore(spawnGroupStore, relationStore);
    }

    @Produces
    @ApplicationScoped
    public WorkItemScheduleCore workItemScheduleCore(WorkItemScheduleService scheduleService,
            WorkItemScheduleStore scheduleStore) {
        return new WorkItemScheduleCore(scheduleService, scheduleStore);
    }

    @Produces
    @ApplicationScoped
    public WorkItemSpawnCore workItemSpawnCore(WorkItemSpawnService spawnService,
            WorkItemSpawnGroupStore spawnGroupStore) {
        return new WorkItemSpawnCore(spawnService, spawnGroupStore);
    }

    @Produces
    @ApplicationScoped
    public WorkItemInstancesCore workItemInstancesCore(WorkItemStore workItemStore,
            WorkItemSpawnGroupStore spawnGroupStore) {
        return new WorkItemInstancesCore(workItemStore, spawnGroupStore);
    }

    @Produces
    @ApplicationScoped
    public WorkItemTemplateCore workItemTemplateCore(WorkItemTemplateService templateService,
            WorkItemTemplateStore templateStore) {
        return new WorkItemTemplateCore(templateService, templateStore);
    }

    @Produces
    @ApplicationScoped
    public LabelRuleCore labelRuleCore(LabelRuleStore labelRuleStore,
            ExpressionEngineRegistry expressionRegistry,
            Instance<LabelRule> permanentRules,
            WorkItemStore workItemStore,
            LabelRuleEngine labelRuleEngine) {
        List<LabelRule> rules = new ArrayList<>();
        permanentRules.forEach(rules::add);
        return new LabelRuleCore(labelRuleStore, expressionRegistry, rules, workItemStore, labelRuleEngine);
    }
}
