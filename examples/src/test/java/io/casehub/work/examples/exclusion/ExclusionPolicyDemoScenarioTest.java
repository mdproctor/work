package io.casehub.work.examples.exclusion;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@QuarkusTest
class ExclusionPolicyDemoScenarioTest {

    @Test
    void run_returnsAllSixResults_withExpectedDecisions() {
        final Response response = given()
                .contentType(ContentType.JSON)
                .when()
                .post("/examples/exclusion-policy/run")
                .then()
                .statusCode(200)
                .extract().response();

        assertThat(response.jsonPath().getString("scenario")).isEqualTo("expiring-exclusion-policy");

        final List<Map<String, Object>> results = response.jsonPath().getList("results");
        assertThat(results).hasSize(6);

        // Step 1: alice, future date (2099-01-01) → DENY with date in reason
        assertThat((Boolean) results.get(0).get("denied")).isTrue();
        assertThat((String) results.get(0).get("reason")).contains("2099-01-01");

        // Step 2: alice, past date (2020-01-01) → ALLOW
        assertThat((Boolean) results.get(1).get("denied")).isFalse();

        // Step 3: bob, future date for alice only → ALLOW (bob not in list)
        assertThat((Boolean) results.get(2).get("denied")).isFalse();

        // Step 4: alice, null exclusion → ALLOW
        assertThat((Boolean) results.get(3).get("denied")).isFalse();

        // Step 5: alice, plain ID (no date) → DENY without "until" in reason
        assertThat((Boolean) results.get(4).get("denied")).isTrue();
        assertThat((String) results.get(4).get("reason")).doesNotContain("until");

        // Step 6: alice, invalid date token → DENY with "invalid expiry format" in reason
        assertThat((Boolean) results.get(5).get("denied")).isTrue();
        assertThat((String) results.get(5).get("reason")).contains("invalid expiry format");
    }
}
