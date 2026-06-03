package io.casehub.work.runtime.api;

import io.casehub.work.api.DeclineTarget;

/**
 * Request body for {@code PUT /workitems/{id}/delegate}.
 *
 * @param to the target actor to delegate to (required)
 * @param declineTarget optional instance-level override for where the item returns
 *        if the delegatee declines; null = use scope preference (default: POOL)
 */
public record DelegateRequest(String to, DeclineTarget declineTarget) {
}
