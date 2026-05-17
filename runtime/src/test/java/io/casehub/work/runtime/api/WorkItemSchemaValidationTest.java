package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * Tests that WorkItemService validates payload against inputDataSchema at create()
 * and resolution against outputDataSchema at complete(). Refs #170.
 */
@QuarkusTest
class WorkItemSchemaValidationTest {

    private static final String OUTPUT_SCHEMA =
            "{\"type\":\"object\",\"required\":[\"decision\"]," +
            "\"properties\":{\"decision\":{\"type\":\"string\"}},\"additionalProperties\":false}";

    private static final String INPUT_SCHEMA =
            "{\"type\":\"object\",\"required\":[\"requestor\"]," +
            "\"properties\":{\"requestor\":{\"type\":\"string\"}},\"additionalProperties\":false}";

    // ── outputDataSchema (resolution validation) ─────────────────────────────

    @Test
    void complete_validResolution_returns200() {
        final String id = workItemReadyToComplete(OUTPUT_SCHEMA);

        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"{\\\"decision\\\":\\\"approved\\\"}\",\"outcome\":null}")
                .put("/workitems/" + id + "/complete?actor=alice")
                .then()
                .statusCode(200);
    }

    @Test
    void complete_invalidResolution_returns400WithViolations() {
        final String id = workItemReadyToComplete(OUTPUT_SCHEMA);

        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"{\\\"wrong_field\\\":\\\"value\\\"}\"}")
                .put("/workitems/" + id + "/complete?actor=alice")
                .then()
                .statusCode(400)
                .body("error", containsString("outputDataSchema"));
    }

    @Test
    void complete_nullResolution_whenOutputSchemaSet_returns200() {
        final String id = workItemReadyToComplete(OUTPUT_SCHEMA);

        given().contentType(ContentType.JSON)
                .body("{}")
                .put("/workitems/" + id + "/complete?actor=alice")
                .then()
                .statusCode(200);
    }

    @Test
    void complete_noOutputSchema_anyResolutionAccepted() {
        final String id = workItemReadyToComplete(null);

        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"{\\\"anything\\\":true}\"}")
                .put("/workitems/" + id + "/complete?actor=alice")
                .then()
                .statusCode(200);
    }

    // ── inputDataSchema (payload validation at create) ────────────────────────

    @Test
    void instantiate_validDefaultPayload_returns201() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Input Schema Template\",\"candidateGroups\":\"ops\"," +
                      "\"inputDataSchema\":" + INPUT_SCHEMA + "," +
                      "\"defaultPayload\":\"{\\\"requestor\\\":\\\"eng-team\\\"}\"," +
                      "\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201);
    }

    @Test
    void instantiate_invalidDefaultPayload_returns400() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Bad Payload Template\",\"candidateGroups\":\"ops\"," +
                      "\"inputDataSchema\":" + INPUT_SCHEMA + "," +
                      "\"defaultPayload\":\"{\\\"wrong_field\\\":\\\"value\\\"}\"," +
                      "\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(400);
    }

    @Test
    void instantiate_nullPayload_whenInputSchemaSet_returns201() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Null Payload Template\",\"candidateGroups\":\"ops\"," +
                      "\"inputDataSchema\":" + INPUT_SCHEMA + ",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201);
    }

    @Test
    void directCreate_noTemplate_noSchemaValidation() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Ad hoc\",\"candidateGroups\":\"ops\",\"createdBy\":\"system\"}")
                .post("/workitems")
                .then()
                .statusCode(201);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String workItemReadyToComplete(final String outputDataSchema) {
        final String schemaJson = outputDataSchema != null
                ? ",\"outputDataSchema\":" + outputDataSchema
                : "";
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Completion Schema\",\"candidateGroups\":\"reviewers\"" +
                      schemaJson + ",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        final String id = given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then().statusCode(201).extract().path("id");

        given().put("/workitems/" + id + "/claim?claimant=alice").then().statusCode(200);
        given().put("/workitems/" + id + "/start?actor=alice").then().statusCode(200);
        return id;
    }
}
