package io.casehub.work.ai.skill;

import io.casehub.work.api.SkillProfile;
import io.casehub.work.api.spi.SkillProfileProvider;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.spi.WorkItemStore;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ResolutionHistorySkillProfileProvider implements SkillProfileProvider {

    private final WorkItemStore workItemStore;
    private final int historyLimit;

    public ResolutionHistorySkillProfileProvider(final WorkItemStore workItemStore, final int historyLimit) {
        this.workItemStore = workItemStore;
        this.historyLimit = historyLimit;
    }

    @Override
    public SkillProfile getProfile(final String workerId, final Set<String> capabilities) {
        final Map<String, Long> frequencies = workItemStore
                .scan(WorkItemQuery.builder()
                        .assigneeId(workerId)
                        .statusIn(List.of(WorkItemStatus.COMPLETED))
                        .build())
                .stream()
                .filter(wi -> workerId.equals(wi.assigneeId())
                        && wi.status() == WorkItemStatus.COMPLETED
                        && !wi.types().isEmpty())
                .sorted(Comparator.comparing(
                        wi -> wi.completedAt() != null ? wi.completedAt() : Instant.EPOCH,
                        Comparator.reverseOrder()))
                .limit(historyLimit)
                .collect(Collectors.groupingBy(wi -> wi.types().iterator().next(), Collectors.counting()));

        if (frequencies.isEmpty()) {
            return SkillProfile.ofNarrative("");
        }

        final String narrative = "Completed work: " + frequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Long> comparingByValue().reversed())
                .map(e -> e.getKey() + "×" + e.getValue())
                .collect(Collectors.joining(", "));

        final Map<String, Object> attributes = new LinkedHashMap<>(frequencies);
        return new SkillProfile(narrative, attributes);
    }
}
