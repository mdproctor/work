package io.casehub.work.examples.formschema;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@QuarkusTest
class FormSchemaScenarioTest {

    @Test
    void run_formSchema_enforceOutputDataSchemaOnTemplate() {
        final Response response = given()
                .contentType(ContentType.JSON)
                .when()
                .post("/examples/formschema/run")
                .then()
                .statusCode(200)
                .extract().response();

        // Scenario identity
        assertThat(response.jsonPath().getString("scenario")).isEqualTo("form-schema");

        // Steps logged (8 steps)
        final List<Map<String, Object>> steps = response.jsonPath().getList("steps");
        assertThat(steps).hasSize(8);

        // Template ID and name present
        assertThat(response.jsonPath().getString("templateId")).isNotNull();
        assertThat(response.jsonPath().getString("templateName")).isEqualTo("Contract Review Form");

        // First WorkItem was created and completed
        assertThat(response.jsonPath().getString("workItemId")).isNotNull();

        // Invalid resolution was correctly rejected
        assertThat(response.jsonPath().getBoolean("invalidRejected")).isTrue();

        // Audit trail for the successfully completed WorkItem: at least CREATED, STARTED, COMPLETED
        final List<Map<String, Object>> audit = response.jsonPath().getList("auditTrail");
        assertThat(audit).isNotEmpty();
        assertThat(audit.stream().anyMatch(e -> "CREATED".equals(e.get("event")))).isTrue();
        assertThat(audit.stream().anyMatch(e -> "STARTED".equals(e.get("event")))).isTrue();
        assertThat(audit.stream().anyMatch(e -> "COMPLETED".equals(e.get("event")))).isTrue();
    }
}
