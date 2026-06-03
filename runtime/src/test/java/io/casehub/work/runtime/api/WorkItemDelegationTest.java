package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * REST integration tests for delegation lifecycle endpoints:
 * {@code PUT /workitems/{id}/accept-delegation} and
 * {@code PUT /workitems/{id}/decline-delegation}.
 *
 * <p>Also covers the {@code GET /workitems/{id}} path through {@link WorkItemService#findById}.
 */
@QuarkusTest
@TestTransaction
class WorkItemDelegationTest {

    private String createAndClaimItem(final String claimant) {
        final String id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"Delegation test","priority":"MEDIUM","createdBy":"system"}
                        """)
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().path("id");

        given().contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/claim?claimant=" + claimant)
                .then().statusCode(200);

        return id;
    }

    private void delegate(final String id, final String actor, final String to) {
        given()
                .contentType(ContentType.JSON)
                .body("{\"to\":\"" + to + "\"}")
                .when().put("/workitems/" + id + "/delegate?actor=" + actor)
                .then().statusCode(200);
    }

    private void delegateWithTarget(final String id, final String actor, final String to,
            final String declineTarget) {
        given()
                .contentType(ContentType.JSON)
                .body("{\"to\":\"" + to + "\",\"declineTarget\":\"" + declineTarget + "\"}")
                .when().put("/workitems/" + id + "/delegate?actor=" + actor)
                .then().statusCode(200);
    }

    // ── delegate endpoint ─────────────────────────────────────────────────────

    @Test
    void delegate_returns200WithDelegatedStatus() {
        final String id = createAndClaimItem("alice");

        given()
                .contentType(ContentType.JSON)
                .body("{\"to\":\"bob\"}")
                .when().put("/workitems/" + id + "/delegate?actor=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("DELEGATED"))
                .body("assigneeId", equalTo("bob"));
    }

    @Test
    void delegate_withDeclineTargetDelegator_storesTarget() {
        final String id = createAndClaimItem("alice");

        given()
                .contentType(ContentType.JSON)
                .body("{\"to\":\"bob\",\"declineTarget\":\"DELEGATOR\"}")
                .when().put("/workitems/" + id + "/delegate?actor=alice")
                .then()
                .statusCode(200)
                .body("delegationDeclineTarget", equalTo("DELEGATOR"));
    }

    // ── GET /{id} — findById path ─────────────────────────────────────────────

    @Test
    void getById_afterDelegate_returnsDelegatedStatus() {
        final String id = createAndClaimItem("alice");
        delegate(id, "alice", "bob");

        given()
                .when().get("/workitems/" + id)
                .then()
                .statusCode(200)
                .body("status", equalTo("DELEGATED"));
    }

    // ── accept-delegation endpoint ────────────────────────────────────────────

    @Test
    void acceptDelegation_returns200WithAssignedStatus() {
        final String id = createAndClaimItem("alice");
        delegate(id, "alice", "bob");

        given()
                .when().put("/workitems/" + id + "/accept-delegation?claimant=bob")
                .then()
                .statusCode(200)
                .body("status", equalTo("ASSIGNED"))
                .body("assigneeId", equalTo("bob"));
    }

    @Test
    void acceptDelegation_clearsDeclineTarget() {
        final String id = createAndClaimItem("alice");
        delegateWithTarget(id, "alice", "bob", "DELEGATOR");

        given()
                .when().put("/workitems/" + id + "/accept-delegation?claimant=bob")
                .then()
                .statusCode(200)
                .body("delegationDeclineTarget", nullValue());
    }

    @Test
    void acceptDelegation_wrongActor_returns409() {
        final String id = createAndClaimItem("alice");
        delegate(id, "alice", "bob");

        given()
                .when().put("/workitems/" + id + "/accept-delegation?claimant=charlie")
                .then().statusCode(409);
    }

    @Test
    void acceptDelegation_notDelegated_returns409() {
        final String id = createAndClaimItem("alice");

        // Item is ASSIGNED, not DELEGATED
        given()
                .when().put("/workitems/" + id + "/accept-delegation?claimant=alice")
                .then().statusCode(409);
    }

    // ── decline-delegation endpoint — POOL ───────────────────────────────────

    @Test
    void declineDelegation_poolPath_returns200WithPendingStatus() {
        final String id = createAndClaimItem("alice");
        delegateWithTarget(id, "alice", "bob", "POOL");

        given()
                .when().put("/workitems/" + id + "/decline-delegation?actor=bob")
                .then()
                .statusCode(200)
                .body("status", equalTo("PENDING"))
                .body("assigneeId", nullValue());
    }

    // ── decline-delegation endpoint — DELEGATOR ───────────────────────────────

    @Test
    void declineDelegation_delegatorPath_returns200WithAssignedToPreviousActor() {
        final String id = createAndClaimItem("alice");
        delegateWithTarget(id, "alice", "bob", "DELEGATOR");

        given()
                .when().put("/workitems/" + id + "/decline-delegation?actor=bob")
                .then()
                .statusCode(200)
                .body("status", equalTo("ASSIGNED"))
                .body("assigneeId", equalTo("alice"));
    }

    // ── decline-delegation error cases ────────────────────────────────────────

    @Test
    void declineDelegation_wrongActor_returns409() {
        final String id = createAndClaimItem("alice");
        delegate(id, "alice", "bob");

        given()
                .when().put("/workitems/" + id + "/decline-delegation?actor=charlie")
                .then().statusCode(409);
    }

    @Test
    void declineDelegation_notDelegated_returns409() {
        final String id = createAndClaimItem("alice");

        given()
                .when().put("/workitems/" + id + "/decline-delegation?actor=alice")
                .then().statusCode(409);
    }
}
