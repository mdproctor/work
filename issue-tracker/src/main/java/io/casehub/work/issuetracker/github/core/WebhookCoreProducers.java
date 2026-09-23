package io.casehub.work.issuetracker.github.core;

import io.casehub.work.issuetracker.github.GitHubIssueTrackerConfig;
import io.casehub.work.issuetracker.jira.JiraIssueTrackerConfig;
import io.casehub.work.issuetracker.jira.core.JiraWebhookCore;
import io.casehub.work.issuetracker.webhook.WebhookEventHandler;
import io.casehub.work.runtime.service.TenantHolder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class WebhookCoreProducers {

    @Produces
    @ApplicationScoped
    public GitHubWebhookCore gitHubWebhookCore(GitHubIssueTrackerConfig config,
            WebhookEventHandler handler, TenantHolder tenantHolder) {
        return new GitHubWebhookCore(config, handler, tenantHolder);
    }

    @Produces
    @ApplicationScoped
    public JiraWebhookCore jiraWebhookCore(JiraIssueTrackerConfig config,
            WebhookEventHandler handler, TenantHolder tenantHolder) {
        return new JiraWebhookCore(config, handler, tenantHolder);
    }
}
