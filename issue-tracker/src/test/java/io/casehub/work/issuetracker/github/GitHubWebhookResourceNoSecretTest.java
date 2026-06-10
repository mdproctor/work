package io.casehub.work.issuetracker.github;

import java.util.Map;

import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestProfile(GitHubWebhookResourceNoSecretTest.NoSecretProfile.class)
class GitHubWebhookResourceNoSecretTest {

    public static class NoSecretProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("casehub.work.issue-tracker.github.webhook-secret", "");
        }
    }

    @Test
    void noSecretConfigured_returns401() {
        given()
            .header("Content-Type", "application/json")
            .header("X-Hub-Signature-256", "sha256=anything")
            .body("{}")
        .when()
            .post("/workitems/github-webhook/" + TenancyConstants.DEFAULT_TENANT_ID)
        .then()
            .statusCode(401);
    }
}
