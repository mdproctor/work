package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * Tests that WorkItemTemplate carries inputDataSchema and outputDataSchema,
 * and that both are snapshotted onto WorkItem at instantiation. Refs #170.
 */
@QuarkusTest
class WorkItemTemplateSchemaTest {

    private static final String OUTPUT_SCHEMA =
            "{\"type\":\"object\",\"required\":[\"decision\"]," +
            "\"properties\":{\"decision\":{\"type\":\"string\"}},\"additionalProperties\":false}";

    private static final String INPUT_SCHEMA =
            "{\"type\":\"object\",\"required\":[\"requestor\"]," +
            "\"properties\":{\"requestor\":{\"type\":\"string\"}},\"additionalProperties\":false}";

    @Test
    void createTemplate_withOutputDataSchema_storesAndReturnsIt() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Loan Approval\",\"category\":\"finance\"," +
                      "\"outputDataSchema\":" + OUTPUT_SCHEMA + ",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then()
                .statusCode(201)
                .body("outputDataSchema", notNullValue())
                .body("inputDataSchema", nullValue());
    }

    @Test
    void createTemplate_withBothSchemas_storesAndReturnsBoth() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Review Task\",\"candidateGroups\":\"reviewers\"," +
                      "\"inputDataSchema\":" + INPUT_SCHEMA + "," +
                      "\"outputDataSchema\":" + OUTPUT_SCHEMA + ",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then()
                .statusCode(201)
                .body("inputDataSchema", notNullValue())
                .body("outputDataSchema", notNullValue());
    }

    @Test
    void instantiateTemplate_withOutputDataSchema_snapshotsOntoWorkItem() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Schema Template\",\"candidateGroups\":\"reviewers\"," +
                      "\"outputDataSchema\":" + OUTPUT_SCHEMA + ",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("outputDataSchema", notNullValue())
                .body("inputDataSchema", nullValue())
                .body("templateId", equalTo(templateId));
    }

    @Test
    void createTemplate_withInputDataSchemaAsString_returns400() {
        // Callers that accidentally double-encode the schema as a JSON string get a
        // clear 400 at creation time rather than a confusing error at validation time.
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Bad Input Schema\",\"category\":\"test\"," +
                      "\"inputDataSchema\":\"" + INPUT_SCHEMA.replace("\"", "\\\"") + "\"," +
                      "\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then()
                .statusCode(400);
    }

    @Test
    void createTemplate_withOutputDataSchemaAsString_returns400() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Bad Output Schema\",\"category\":\"test\"," +
                      "\"outputDataSchema\":\"" + OUTPUT_SCHEMA.replace("\"", "\\\"") + "\"," +
                      "\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then()
                .statusCode(400);
    }

    @Test
    void instantiateTemplate_withoutSchemas_workItemSchemasAreNull() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"No Schema\",\"candidateGroups\":\"ops\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("outputDataSchema", nullValue())
                .body("inputDataSchema", nullValue());
    }
}
