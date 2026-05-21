package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for named outcomes declared on WorkItemTemplate — CRUD and instantiation snapshot.
 * Refs #169.
 */
@QuarkusTest
class WorkItemTemplateOutcomeTest {

    @BeforeEach
    @Transactional
    void clearTemplates() {
        AuditEntry.deleteAll();
        WorkItem.deleteAll();
        WorkItemTemplate.deleteAll();
    }

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

    // -------------------------------------------------------------------------
    // reject() with named outcomes — Refs #176
    // -------------------------------------------------------------------------

    @Test
    void reject_withPermittedOutcome_setsOutcomeOnWorkItem() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Reject Outcome Template","createdBy":"admin",
                         "outcomes":[
                           {"name":"approved","displayName":"Approved"},
                           {"name":"rejected-conflict","displayName":"Conflict of Interest"}
                         ]}
                        """)
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        final String id = given().contentType(ContentType.JSON)
                .body("""
                        {"createdBy":"system"}
                        """)
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then().statusCode(201).extract().path("id");

        given().put("/workitems/" + id + "/claim?claimant=alice").then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body("""
                        {"reason":"conflict of interest","outcome":"rejected-conflict"}
                        """)
                .put("/workitems/" + id + "/reject?actor=alice")
                .then()
                .statusCode(200)
                .body("outcome", equalTo("rejected-conflict"))
                .body("status", equalTo("REJECTED"));
    }

    @Test
    void reject_withUnpermittedOutcome_returns400() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Reject Outcome Template2","createdBy":"admin",
                         "outcomes":[
                           {"name":"approved","displayName":"Approved"}
                         ]}
                        """)
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        final String id = given().contentType(ContentType.JSON)
                .body("""
                        {"createdBy":"system"}
                        """)
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then().statusCode(201).extract().path("id");

        given().put("/workitems/" + id + "/claim?claimant=alice").then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body("""
                        {"reason":"no good","outcome":"not-a-real-outcome"}
                        """)
                .put("/workitems/" + id + "/reject?actor=alice")
                .then()
                .statusCode(400)
                .body("error", containsString("not-a-real-outcome"));
    }

    @Test
    void reject_withNoPermittedOutcomes_outcomeIsOptional() {
        final String id = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"No outcome constraint","createdBy":"system"}
                        """)
                .post("/workitems")
                .then().statusCode(201).extract().path("id");

        given().put("/workitems/" + id + "/claim?claimant=bob").then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body("""
                        {"reason":"not suitable","outcome":"any-value"}
                        """)
                .put("/workitems/" + id + "/reject?actor=bob")
                .then()
                .statusCode(200)
                .body("outcome", equalTo("any-value"))
                .body("status", equalTo("REJECTED"));
    }

    @Test
    void reject_withNoOutcome_whenPermittedDeclared_returns400() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Reject Null Outcome Template","createdBy":"admin",
                         "outcomes":[{"name":"approved","displayName":"Approved"}]}
                        """)
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        final String id = given().contentType(ContentType.JSON)
                .body("""
                        {"createdBy":"system"}
                        """)
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then().statusCode(201).extract().path("id");

        given().put("/workitems/" + id + "/claim?claimant=alice").then().statusCode(200);

        // Reject with no outcome — should fail since template declares permitted outcomes
        given().contentType(ContentType.JSON)
                .body("""
                        {"reason":"not good"}
                        """)
                .put("/workitems/" + id + "/reject?actor=alice")
                .then()
                .statusCode(400)
                .body("error", containsString("outcome is required"));
    }
}
