package io.casehub.work.ai.skill.core;

import io.casehub.work.ai.repository.WorkerSkillProfileStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class SkillCoreProducers {

    @Produces
    @ApplicationScoped
    public WorkerSkillProfileCore workerSkillProfileCore(WorkerSkillProfileStore profileStore) {
        return new WorkerSkillProfileCore(profileStore);
    }
}
