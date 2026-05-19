package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItemTemplate;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Tests for strict named-outcome validation in PUT /workitems/{id}/complete.
 *
 * <p>
 * When a WorkItem was instantiated from a template that declares outcomes,
 * complete() must reject any outcome not in the declared list, and must
 * require an outcome to be provided. Refs #169.
 */
@QuarkusTest
class WorkItemOutcomeValidationTest {

    @BeforeEach
    @Transactional
    void clearTemplates() {
        WorkItemTemplate.deleteAll();
    }

    /** Create a template with outcomes, instantiate, claim, start — returns the workItem id. */
    private String workItemReadyToComplete(final String... outcomeNames) {
        // Build outcomes JSON
        final var outcomes = new StringBuilder("[");
        for (int i = 0; i < outcomeNames.length; i++) {
            if (i > 0) outcomes.append(",");
            outcomes.append("{\"name\":\"").append(outcomeNames[i])
                    .append("\",\"displayName\":\"").append(outcomeNames[i]).append("\"}");
        }
        outcomes.append("]");

        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Approval\",\"candidateGroups\":\"reviewers\"," +
                      "\"outcomes\":" + outcomes + ",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        final String workItemId = given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then().statusCode(201).extract().path("id");

        given().put("/workitems/" + workItemId + "/claim?claimant=alice")
                .then().statusCode(200);
        given().put("/workitems/" + workItemId + "/start?actor=alice")
                .then().statusCode(200);

        return workItemId;
    }

    @Test
    void complete_withValidOutcome_returns200_andSetsOutcome() {
        final String id = workItemReadyToComplete("approved", "rejected");

        given().contentType(ContentType.JSON)
                .body("{\"outcome\":\"approved\"}")
                .put("/workitems/" + id + "/complete?actor=alice")
                .then()
                .statusCode(200)
                .body("outcome", equalTo("approved"))
                .body("status", equalTo("COMPLETED"));
    }

    @Test
    void complete_withInvalidOutcome_returns400() {
        final String id = workItemReadyToComplete("approved", "rejected");

        given().contentType(ContentType.JSON)
                .body("{\"outcome\":\"deferred\"}")
                .put("/workitems/" + id + "/complete?actor=alice")
                .then()
                .statusCode(400);
    }

    @Test
    void complete_withNoOutcome_whenPermittedDeclared_returns400() {
        final String id = workItemReadyToComplete("approved", "rejected");

        given().contentType(ContentType.JSON)
                .body("{}")
                .put("/workitems/" + id + "/complete?actor=alice")
                .then()
                .statusCode(400);
    }

    @Test
    void complete_withOutcome_whenNoPermittedDeclared_returns200() {
        // WorkItem created directly (not from template) — no permittedOutcomes constraint
        final String workItemId = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Ad hoc task\",\"candidateGroups\":\"ops\",\"createdBy\":\"system\"}")
                .post("/workitems")
                .then().statusCode(201).extract().path("id");

        given().put("/workitems/" + workItemId + "/claim?claimant=alice")
                .then().statusCode(200);
        given().put("/workitems/" + workItemId + "/start?actor=alice")
                .then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body("{\"outcome\":\"any-value-accepted\"}")
                .put("/workitems/" + workItemId + "/complete?actor=alice")
                .then()
                .statusCode(200)
                .body("outcome", equalTo("any-value-accepted"));
    }

    @Test
    void complete_withNoOutcome_whenNoPermittedDeclared_returns200() {
        // Existing behaviour preserved — no outcome required when template has no outcomes
        final String workItemId = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Legacy task\",\"candidateGroups\":\"ops\",\"createdBy\":\"system\"}")
                .post("/workitems")
                .then().statusCode(201).extract().path("id");

        given().put("/workitems/" + workItemId + "/claim?claimant=alice")
                .then().statusCode(200);
        given().put("/workitems/" + workItemId + "/start?actor=alice")
                .then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"done\"}")
                .put("/workitems/" + workItemId + "/complete?actor=alice")
                .then()
                .statusCode(200)
                .body("outcome", equalTo(null));
    }
}
