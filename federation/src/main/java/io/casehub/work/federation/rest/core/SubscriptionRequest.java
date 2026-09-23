package io.casehub.work.federation.rest.core;

import java.util.List;

public record SubscriptionRequest(
        String peerId,
        String callbackUrl,
        String baseUrl,
        String tenancyId,
        FilterRequest filter,
        String capabilitiesJson,
        String hmacSecret) {

    public record FilterRequest(
            List<String> candidateGroups,
            List<String> candidateUsers) {
    }
}
