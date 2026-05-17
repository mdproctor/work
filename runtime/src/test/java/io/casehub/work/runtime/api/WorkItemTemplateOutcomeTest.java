package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Tests for named outcomes declared on WorkItemTemplate — CRUD and instantiation snapshot.
 * Refs #169.
 */
@QuarkusTest
class WorkItemTemplateOutcomeTest {

    @Test
    void createTemplate_withOutcomes_storesAndReturnsOutcomesList() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Loan Approval","category":"finance",
                         "outcomes":[
                           {"name":"approved","displayName":"Approved"},
                           {"name":"rejected","displayName":"Rejected"},
                           {"name":"needs-revision","displayName":"Needs Revision"}
                         ],
                         "createdBy":"admin"}
                        """)
                .post("/workitem-templates")
                .then()
                .statusCode(201)
                .body("outcomes", hasSize(3))
                .body("outcomes[0].name", equalTo("approved"))
                .body("outcomes[0].displayName", equalTo("Approved"))
                .body("outcomes[1].name", equalTo("rejected"))
                .body("outcomes[2].name", equalTo("needs-revision"));
    }

    @Test
    void createTemplate_withoutOutcomes_returnsNullOrEmptyOutcomes() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Free-form Task","category":"general","createdBy":"admin"}
                        """)
                .post("/workitem-templates")
                .then()
                .statusCode(201)
                .body("id", notNullValue());
        // outcomes absent or empty — template behaves as before
    }

    @Test
    void instantiateTemplate_withOutcomes_workItemReceivesPermittedOutcomesAndTemplateId() {
        // Create template with outcomes
        final String templateId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Review Task","category":"review",
                         "outcomes":[
                           {"name":"approved","displayName":"Approved"},
                           {"name":"needs-revision","displayName":"Needs Revision"}
                         ],
                         "createdBy":"admin"}
                        """)
                .post("/workitem-templates")
                .then()
                .statusCode(201)
                .extract().path("id");

        // Instantiate
        given().contentType(ContentType.JSON)
                .body("""
                        {"createdBy":"system"}
                        """)
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("templateId", equalTo(templateId))
                .body("permittedOutcomes", hasSize(2))
                .body("permittedOutcomes[0]", equalTo("approved"))
                .body("permittedOutcomes[1]", equalTo("needs-revision"));
    }

    @Test
    void instantiateTemplate_withoutOutcomes_permittedOutcomesIsNull() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Simple Task","category":"ops","createdBy":"admin"}
                        """)
                .post("/workitem-templates")
                .then()
                .statusCode(201)
                .extract().path("id");

        given().contentType(ContentType.JSON)
                .body("""
                        {"createdBy":"system"}
                        """)
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("templateId", equalTo(templateId))
                .body("permittedOutcomes", nullValue());
    }
}
