package io.casehub.work.ai.skill.core;

import java.util.List;
import java.util.Optional;

import jakarta.transaction.Transactional;

import io.casehub.work.ai.repository.WorkerSkillProfileStore;
import io.casehub.work.ai.skill.WorkerSkillProfile;

public class WorkerSkillProfileCore {

    private final WorkerSkillProfileStore profileStore;

    public WorkerSkillProfileCore(WorkerSkillProfileStore profileStore) {
        this.profileStore = profileStore;
    }

    @Transactional
    public void upsert(ProfileRequest request) {
        if (request == null || request.workerId() == null || request.workerId().isBlank()) {
            throw new IllegalArgumentException("workerId is required");
        }
        var existing = profileStore.get(request.workerId());
        if (existing.isEmpty()) {
            var profile = new WorkerSkillProfile();
            profile.workerId = request.workerId();
            profile.narrative = request.narrative();
            profileStore.put(profile);
        } else {
            existing.get().narrative = request.narrative();
            profileStore.put(existing.get());
        }
    }

    public List<WorkerSkillProfile> listAll() {
        return profileStore.scanAll();
    }

    public Optional<WorkerSkillProfile> get(String workerId) {
        return profileStore.get(workerId);
    }

    @Transactional
    public boolean delete(String workerId) {
        return profileStore.delete(workerId);
    }
}
