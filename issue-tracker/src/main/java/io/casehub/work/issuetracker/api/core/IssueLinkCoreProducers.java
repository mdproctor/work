package io.casehub.work.issuetracker.api.core;

import io.casehub.work.issuetracker.service.IssueLinkService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class IssueLinkCoreProducers {

    @Produces
    @ApplicationScoped
    public IssueLinkCore issueLinkCore(IssueLinkService linkService) {
        return new IssueLinkCore(linkService);
    }
}
