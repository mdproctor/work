package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.casehub.work.runtime.model.WorkItemTemplate;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that excludedUsers is enforced at all five assignment paths. Refs #171, #186.
 */
@QuarkusTest
class WorkItemExcludedUsersTest {

    @BeforeEach
    @Transactional
    void clearTemplates() {
        WorkItemTemplate.deleteAll();
    }

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

    // ── instantiate() assigneeId override vs template excludedUsers ──────────

    @Test
    void instantiateTemplate_withExcludedAssigneeIdOverride_returns400() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Excl Override\",\"candidateGroups\":\"reviewers\"," +
                      "\"excludedUsers\":\"alice\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"assigneeId\":\"alice\",\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(400);
    }

    @Test
    void instantiateTemplate_withNonExcludedAssigneeIdOverride_returns201() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Excl Override OK\",\"candidateGroups\":\"reviewers\"," +
                      "\"excludedUsers\":\"alice\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"assigneeId\":\"bob\",\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201);
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

    // ── Audit trail for blocked attempts ──────────────────────────────────

    @Test
    void claim_byExcludedUser_createsClaimDeniedAuditEntry() {
        final String id = workItemWithExcludedUser("alice");
        given().put("/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(409);

        given().get("/workitems/" + id)
                .then().statusCode(200)
                .body("auditTrail.find { it.event == 'CLAIM_DENIED' }.actor", equalTo("alice"))
                .body("auditTrail.find { it.event == 'CLAIM_DENIED' }.detail", notNullValue());
    }

    @Test
    void claim_byExcludedUser_auditEntryPersistsDespiteRejection() {
        // Verifies the blocked attempt audit entry is durably persisted and readable after a rejected claim.
        final String id = workItemWithExcludedUser("alice");
        given().put("/workitems/" + id + "/claim?claimant=alice").then().statusCode(409);

        // WorkItem must still be PENDING (claim was rejected)
        given().get("/workitems/" + id)
                .then().statusCode(200)
                .body("status", equalTo("PENDING"))
                .body("auditTrail.collect { it.event }.flatten()", org.hamcrest.Matchers.hasItem("CLAIM_DENIED"));
    }

    @Test
    void delegate_toExcludedUser_createsDelegateDeniedAuditEntry() {
        final String id = workItemWithExcludedUser("alice");
        given().put("/workitems/" + id + "/claim?claimant=bob").then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body("{\"to\":\"alice\"}")
                .put("/workitems/" + id + "/delegate?actor=bob")
                .then().statusCode(400);

        given().get("/workitems/" + id)
                .then().statusCode(200)
                .body("auditTrail.find { it.event == 'DELEGATE_DENIED' }.actor", equalTo("bob"))
                .body("auditTrail.find { it.event == 'DELEGATE_DENIED' }.detail", org.hamcrest.Matchers.containsString("alice"));
    }

    @Test
    void claim_byAllowedUser_doesNotCreateClaimDeniedEntry() {
        final String id = workItemWithExcludedUser("alice");
        given().put("/workitems/" + id + "/claim?claimant=bob").then().statusCode(200);

        given().get("/workitems/" + id)
                .then().statusCode(200)
                .body("auditTrail.collect { it.event }.flatten()", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("CLAIM_DENIED")));
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
