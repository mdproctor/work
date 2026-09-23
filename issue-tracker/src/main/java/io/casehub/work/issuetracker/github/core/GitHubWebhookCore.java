package io.casehub.work.issuetracker.github.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.jboss.logging.Logger;

import io.casehub.work.issuetracker.github.GitHubIssueTrackerConfig;
import io.casehub.work.issuetracker.github.GitHubWebhookParser;
import io.casehub.work.issuetracker.webhook.WebhookEvent;
import io.casehub.work.issuetracker.webhook.WebhookEventHandler;
import io.casehub.work.runtime.service.TenantHolder;

public class GitHubWebhookCore {

    private static final Logger LOG = Logger.getLogger(GitHubWebhookCore.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    private final GitHubIssueTrackerConfig config;
    private final GitHubWebhookParser parser = new GitHubWebhookParser();
    private final WebhookEventHandler handler;
    private final TenantHolder tenantHolder;

    public GitHubWebhookCore(GitHubIssueTrackerConfig config,
            WebhookEventHandler handler, TenantHolder tenantHolder) {
        this.config = config;
        this.handler = handler;
        this.tenantHolder = tenantHolder;
    }

    public WebhookOutcome receive(String tenancyId, String signature, String body) {
        if (tenancyId == null || tenancyId.isBlank()) {
            return WebhookOutcome.BAD_REQUEST;
        }

        String secret = config.webhookSecret().filter(s -> !s.isBlank()).orElse(null);
        if (secret == null) {
            LOG.warn("GitHub webhook received but casehub.work.issue-tracker.github.webhook-secret is not configured — rejecting");
            return WebhookOutcome.UNAUTHORIZED;
        }

        if (!verifySignature(secret, body, signature)) {
            LOG.warn("GitHub webhook HMAC verification failed — rejecting");
            return WebhookOutcome.UNAUTHORIZED;
        }

        tenantHolder.setTenancyId(tenancyId);

        try {
            WebhookEvent event = parser.parse(Map.of(), body);
            if (event != null) {
                handler.handle(event);
            }
        } catch (Exception e) {
            LOG.warnf("GitHub webhook processing error (returning 200 to prevent retry): %s", e.getMessage());
        }

        return WebhookOutcome.OK;
    }

    private boolean verifySignature(String secret, String body, String signature) {
        if (signature == null || signature.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            String expected = "sha256=" +
                    HEX.formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOG.warnf("HMAC computation failed: %s", e.getMessage());
            return false;
        }
    }
}
