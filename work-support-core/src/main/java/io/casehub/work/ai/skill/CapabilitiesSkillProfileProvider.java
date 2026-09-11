package io.casehub.work.ai.skill;

import io.casehub.work.api.SkillProfile;
import io.casehub.work.api.spi.SkillProfileProvider;

import java.util.Set;

public class CapabilitiesSkillProfileProvider implements SkillProfileProvider {

    @Override
    public SkillProfile getProfile(final String workerId, final Set<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return SkillProfile.ofNarrative("");
        }
        return SkillProfile.ofNarrative(String.join(", ", capabilities));
    }
}
