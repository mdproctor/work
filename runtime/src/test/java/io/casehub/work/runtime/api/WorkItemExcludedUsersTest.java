package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * Tests that excludedUsers is enforced at all five assignment paths. Refs #171.
 */
@QuarkusTest
class WorkItemExcludedUsersTest {

    // ── Template CRUD + snapshot ────────────────────────────────────────────

    @Test
    void createTemplate_withExcludedUsers_storesAndReturnsIt() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Approval\",\"candidateGroups\":\"reviewers\"," +
                      "\"excludedUsers\":\"alice,bob\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then()
                .statusCode(201)
                .body("excludedUsers", equalTo("alice,bob"));
    }

    @Test
    void instantiateTemplate_withExcludedUsers_snapshotsOntoWorkItem() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Exclusion Template\",\"candidateGroups\":\"reviewers\"," +
                      "\"excludedUsers\":\"alice\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("excludedUsers", equalTo("alice"));
    }

    @Test
    void instantiateTemplate_withoutExcludedUsers_workItemExcludedUsersIsNull() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"No Exclusion\",\"candidateGroups\":\"ops\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("excludedUsers", nullValue());
    }

    // ── Claim enforcement ──────────────────────────────────────────────────

    @Test
    void claim_byExcludedUser_returns409() {
        final String id = workItemWithExcludedUser("alice");
        given().put("/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(409);
    }

    @Test
    void claim_byNonExcludedUser_returns200() {
        final String id = workItemWithExcludedUser("alice");
        given().put("/workitems/" + id + "/claim?claimant=bob")
                .then().statusCode(200);
    }

    // ── Direct assigneeId at creation enforcement ─────────────────────────

    @Test
    void createWorkItem_withExcludedAssigneeId_returns400() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Conflict task\",\"candidateGroups\":\"ops\"," +
                      "\"assigneeId\":\"alice\",\"excludedUsers\":\"alice\",\"createdBy\":\"system\"}")
                .post("/workitems")
                .then()
                .statusCode(400);
    }

    @Test
    void createWorkItem_withNonExcludedAssigneeId_returns201() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"OK task\",\"candidateGroups\":\"ops\"," +
                      "\"assigneeId\":\"bob\",\"excludedUsers\":\"alice\",\"createdBy\":\"system\"}")
                .post("/workitems")
                .then()
                .statusCode(201);
    }

    // ── Delegation enforcement ─────────────────────────────────────────────

    @Test
    void delegate_toExcludedUser_returns400() {
        final String id = workItemWithExcludedUser("alice");
        given().put("/workitems/" + id + "/claim?claimant=bob").then().statusCode(200);
        given().contentType(ContentType.JSON)
                .body("{\"to\":\"alice\"}")
                .put("/workitems/" + id + "/delegate?actor=bob")
                .then()
                .statusCode(400);
    }

    @Test
    void delegate_toNonExcludedUser_returns200() {
        final String id = workItemWithExcludedUser("alice");
        given().put("/workitems/" + id + "/claim?claimant=bob").then().statusCode(200);
        given().contentType(ContentType.JSON)
                .body("{\"to\":\"carol\"}")
                .put("/workitems/" + id + "/delegate?actor=bob")
                .then()
                .statusCode(200);
    }

    // ── No exclusion when excludedUsers is null ────────────────────────────

    @Test
    void claim_noExclusion_whenExcludedUsersNull_returns200() {
        final String workItemId = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Open task\",\"candidateGroups\":\"ops\",\"createdBy\":\"system\"}")
                .post("/workitems")
                .then().statusCode(201).extract().path("id");
        given().put("/workitems/" + workItemId + "/claim?claimant=alice")
                .then().statusCode(200);
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private String workItemWithExcludedUser(final String excludedUser) {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"Exclusion test\",\"candidateGroups\":\"ops\"," +
                      "\"excludedUsers\":\"" + excludedUser + "\",\"createdBy\":\"system\"}")
                .post("/workitems")
                .then().statusCode(201).extract().path("id");
    }
}
