package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.util.Map;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.Capability;
import io.casehub.work.api.CapabilityRegistry;
import io.casehub.work.api.WorkCapabilities;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Integration tests for capability validation wired into POST /workitems.
 * Runs with STRICT mode and a registry containing only {@code legal-review}.
 * Refs #220.
 */
@QuarkusTest
@TestProfile(WorkItemCapabilityIT.StrictRegistryProfile.class)
class WorkItemCapabilityIT {

    public static class StrictRegistryProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("casehub.work.capability-validation", "STRICT");
        }
    }

    /**
     * Displaces {@code PermissiveCapabilityRegistry} (@DefaultBean) with a registry
     * that knows only {@code legal-review}. Static inner class — discovered by Quarkus
     * test CDI scanning because the test class is on the test classpath.
     */
    @Alternative
    @Priority(1)
    @ApplicationScoped
    public static class LegalOnlyRegistry implements CapabilityRegistry {
        @Override
        public Set<Capability> capabilities() {
            return Set.of(WorkCapabilities.LEGAL_REVIEW);
        }
    }

    @Test
    void create_succeeds_whenCapabilityIsInRegistry() {
        given()
            .contentType("application/json")
            .body("""
                {"title":"Review contract","requiredCapabilities":"legal-review","createdBy":"test"}
                """)
        .when()
            .post("/workitems")
        .then()
            .statusCode(201);
    }

    @Test
    void create_returns400_whenCapabilityHasWrongFormat() {
        given()
            .contentType("application/json")
            .body("""
                {"title":"Review contract","requiredCapabilities":"legal_review","createdBy":"test"}
                """)
        .when()
            .post("/workitems")
        .then()
            .statusCode(400)
            .body("error", equalTo("MALFORMED_CAPABILITY"))
            .body("values", hasItem("legal_review"));
    }

    @Test
    void create_returns400_whenCapabilityNotInRegistry() {
        given()
            .contentType("application/json")
            .body("""
                {"title":"Sign document","requiredCapabilities":"audit-sign","createdBy":"test"}
                """)
        .when()
            .post("/workitems")
        .then()
            .statusCode(400)
            .body("error", equalTo("UNKNOWN_CAPABILITY"))
            .body("values", hasItem("audit-sign"));
    }

    @Test
    void create_returns400_withAllUnknownCapabilities_whenMultipleUnknown() {
        given()
            .contentType("application/json")
            .body("""
                {"title":"Multi","requiredCapabilities":"audit-sign,risk-assess","createdBy":"test"}
                """)
        .when()
            .post("/workitems")
        .then()
            .statusCode(400)
            .body("error", equalTo("UNKNOWN_CAPABILITY"))
            .body("values", containsInAnyOrder("audit-sign", "risk-assess"));
    }

    @Test
    void create_succeeds_withNoRequiredCapabilities() {
        given()
            .contentType("application/json")
            .body("""
                {"title":"Open task","createdBy":"test"}
                """)
        .when()
            .post("/workitems")
        .then()
            .statusCode(201);
    }
}
