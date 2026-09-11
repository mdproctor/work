package io.casehub.work.ai.skill;

import io.casehub.work.api.SkillProfile;
import io.casehub.work.api.spi.SkillProfileProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CompositeSkillProfileProvider implements SkillProfileProvider {

    private final List<SkillProfileProvider> delegates;

    public CompositeSkillProfileProvider(final List<SkillProfileProvider> delegates) {
        this.delegates = delegates;
    }

    @Override
    public SkillProfile getProfile(final String workerId, final Set<String> capabilities) {
        final List<String> narratives = new ArrayList<>();
        final Map<String, Object> attrs = new LinkedHashMap<>();

        for (final SkillProfileProvider provider : delegates) {
            if (provider instanceof CompositeSkillProfileProvider) {
                continue;
            }
            final SkillProfile profile = provider.getProfile(workerId, capabilities);
            if (profile.narrative() != null && !profile.narrative().isBlank()) {
                narratives.add(profile.narrative());
            }
            if (profile.attributes() != null) {
                attrs.putAll(profile.attributes());
            }
        }

        final String combined = narratives.stream()
                .collect(Collectors.joining("\n"));
        return new SkillProfile(combined.isBlank() ? null : combined, Map.copyOf(attrs));
    }
}
