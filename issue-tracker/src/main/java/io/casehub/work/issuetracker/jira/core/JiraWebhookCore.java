package io.casehub.work.issuetracker.jira.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import org.jboss.logging.Logger;

import io.casehub.work.issuetracker.github.core.WebhookOutcome;
import io.casehub.work.issuetracker.jira.JiraIssueTrackerConfig;
import io.casehub.work.issuetracker.jira.JiraWebhookParser;
import io.casehub.work.issuetracker.webhook.WebhookEvent;
import io.casehub.work.issuetracker.webhook.WebhookEventHandler;
import io.casehub.work.runtime.service.TenantHolder;

public class JiraWebhookCore {

    private static final Logger LOG = Logger.getLogger(JiraWebhookCore.class);

    private final JiraIssueTrackerConfig config;
    private final JiraWebhookParser parser = new JiraWebhookParser();
    private final WebhookEventHandler handler;
    private final TenantHolder tenantHolder;

    public JiraWebhookCore(JiraIssueTrackerConfig config,
            WebhookEventHandler handler, TenantHolder tenantHolder) {
        this.config = config;
        this.handler = handler;
        this.tenantHolder = tenantHolder;
    }

    public WebhookOutcome receive(String tenancyId, String secret, String body) {
        if (tenancyId == null || tenancyId.isBlank()) {
            return WebhookOutcome.BAD_REQUEST;
        }

        String configuredSecret = config.webhookSecret()
                .filter(s -> !s.isBlank())
                .orElse(null);
        if (configuredSecret == null) {
            LOG.warn("Jira webhook received but casehub.work.issue-tracker.jira.webhook-secret is not configured — rejecting");
            return WebhookOutcome.UNAUTHORIZED;
        }

        if (!verifySecret(configuredSecret, secret)) {
            LOG.warn("Jira webhook secret mismatch — rejecting");
            return WebhookOutcome.UNAUTHORIZED;
        }

        tenantHolder.setTenancyId(tenancyId);

        try {
            WebhookEvent event = parser.parse(Map.of(), body);
            if (event != null) {
                handler.handle(event);
            }
        } catch (Exception e) {
            LOG.warnf("Jira webhook processing error (returning 200 to prevent retry): %s", e.getMessage());
        }

        return WebhookOutcome.OK;
    }

    private boolean verifySecret(String expected, String provided) {
        if (provided == null || provided.isBlank()) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
