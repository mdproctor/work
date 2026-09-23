package io.casehub.work.federation.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.work.api.WorkItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class FederationSubscriptionService {

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public FederationSubscriptionEntity register(String peerId, String callbackUrl,
                                                  String baseUrl, String tenancyId,
                                                  SubscriptionFilter filter,
                                                  String capabilitiesJson, byte[] hmacSecret) {
        var entity = new FederationSubscriptionEntity();
        entity.id = UUID.randomUUID();
        entity.peerId = peerId;
        entity.callbackUrl = callbackUrl;
        entity.baseUrl = baseUrl;
        entity.tenancyId = tenancyId;
        entity.filterJson = serializeFilter(filter);
        entity.capabilitiesJson = capabilitiesJson;
        entity.hmacSecretEncrypted = hmacSecret;
        entity.status = FederationSubscriptionEntity.SubscriptionStatus.ACTIVE;
        entity.consecutiveFailures = 0;
        entity.createdAt = Instant.now();
        entity.persistAndFlush();
        return entity;
    }

    public List<FederationSubscriptionEntity> findActiveByPeerId(String peerId) {
        return FederationSubscriptionEntity.<FederationSubscriptionEntity>find(
                "peerId = ?1 and status = ?2",
                peerId, FederationSubscriptionEntity.SubscriptionStatus.ACTIVE).list();
    }

    public List<FederationSubscriptionEntity> findActiveSubscriptions(String tenancyId) {
        return FederationSubscriptionEntity.find("tenancyId = ?1 and status = ?2",
                        tenancyId, FederationSubscriptionEntity.SubscriptionStatus.ACTIVE)
                .list();
    }

    public List<FederationSubscriptionEntity> matchSubscriptions(WorkItem workItem) {
        List<FederationSubscriptionEntity> active = findActiveSubscriptions(workItem.tenancyId());
        return active.stream()
                .filter(sub -> {
                    SubscriptionFilter filter = deserializeFilter(sub.filterJson);
                    return SubscriptionFilterEvaluator.matches(filter, workItem);
                })
                .toList();
    }

    @Transactional
    public void lockOn(UUID subscriptionId, UUID workItemId) {
        var tracking = new FederationTrackingEntity();
        tracking.subscriptionId = subscriptionId;
        tracking.workItemId = workItemId;
        tracking.persistAndFlush();
    }

    public List<FederationSubscriptionEntity> findLockedSubscriptions(UUID workItemId) {
        List<FederationTrackingEntity> trackings = FederationTrackingEntity.find(
                "workItemId = ?1", workItemId).list();
        List<UUID> subscriptionIds = trackings.stream()
                .map(t -> t.subscriptionId).toList();
        if (subscriptionIds.isEmpty()) {
            return List.of();
        }
        return FederationSubscriptionEntity.find("id in ?1 and status = ?2",
                        subscriptionIds, FederationSubscriptionEntity.SubscriptionStatus.ACTIVE)
                .list();
    }

    @Transactional
    public void removeTracking(UUID workItemId) {
        FederationTrackingEntity.delete("workItemId = ?1", workItemId);
    }

    @Transactional
    public boolean deregister(UUID id) {
        FederationSubscriptionEntity sub = FederationSubscriptionEntity.findById(id);
        if (sub == null) {
            return false;
        }
        sub.status = FederationSubscriptionEntity.SubscriptionStatus.DEREGISTERED;
        return true;
    }

    @Transactional
    public java.util.Optional<java.util.Map<String, Object>> reactivate(UUID id) {
        FederationSubscriptionEntity sub = FederationSubscriptionEntity.findById(id);
        if (sub == null) {
            return java.util.Optional.empty();
        }
        sub.status = FederationSubscriptionEntity.SubscriptionStatus.ACTIVE;
        sub.consecutiveFailures = 0;
        return java.util.Optional.of(java.util.Map.of("id", sub.id, "status", sub.status));
    }

    @Transactional
    public void recordSuccess(UUID subscriptionId) {
        FederationSubscriptionEntity sub = FederationSubscriptionEntity.findById(subscriptionId);
        if (sub != null) {
            sub.consecutiveFailures = 0;
        }
    }

    @Transactional
    public void recordFailure(UUID subscriptionId) {
        FederationSubscriptionEntity sub = FederationSubscriptionEntity.findById(subscriptionId);
        if (sub != null) {
            sub.consecutiveFailures++;
            sub.lastFailureAt = Instant.now();
            if (sub.consecutiveFailures >= 5) {
                sub.status = FederationSubscriptionEntity.SubscriptionStatus.SUSPENDED;
            }
        }
    }

    private String serializeFilter(SubscriptionFilter filter) {
        try {
            return objectMapper.writeValueAsString(filter);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize subscription filter", e);
        }
    }

    SubscriptionFilter deserializeFilter(String json) {
        try {
            return objectMapper.readValue(json, SubscriptionFilter.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize subscription filter", e);
        }
    }
}
