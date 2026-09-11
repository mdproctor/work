package io.casehub.work.engine;

import io.casehub.engine.common.spi.InboundWorkItemRequest;
import io.casehub.engine.common.spi.InboundWorkItemScheduler;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.runtime.service.TenantContextRunner;
import io.casehub.work.api.spi.WorkItemCreator;
public class InboundWorkItemSchedulerImpl implements InboundWorkItemScheduler {

  private final WorkItemCreator workItemCreator;
  private final TenantContextRunner tenantContextRunner;

  public InboundWorkItemSchedulerImpl(
      final WorkItemCreator workItemCreator,
      final TenantContextRunner tenantContextRunner) {
    this.workItemCreator = workItemCreator;
    this.tenantContextRunner = tenantContextRunner;
  }

  @Override
  public void schedule(final InboundWorkItemRequest request) {
    final WorkItemCreateRequest createRequest = WorkItemCreateRequest.builder()
        .title(request.title())
        .description(request.description())
        .candidateGroups(request.candidateGroups())
        .candidateUsers(request.candidateUsers())
        .callerRef(request.callerRef())
        .scope(request.scope())
        .payload(request.payload())
        .tenancyId(request.tenancyId())
        .createdBy(request.createdBy())
        .priority(request.priority() != null
            ? WorkItemPriority.valueOf(request.priority()) : null)
        .types(request.types())
        .expiresAt(request.expiresAt())
        .build();

    tenantContextRunner.runInTenantContext(
        request.tenancyId(), () -> workItemCreator.create(createRequest));
  }
}
