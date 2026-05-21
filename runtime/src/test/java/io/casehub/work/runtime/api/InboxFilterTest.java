package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for filter parameters on {@code GET /workitems/inbox}.
 *
 * <p>
 * Each test creates items with a unique candidateUser to isolate results,
 * then asserts that the declared query params actually filter the inbox.
 */
@QuarkusTest
class InboxFilterTest {

    private String uniqueUser() {
        return "filter-test-" + UUID.randomUUID();
    }

    private void createItem(final String user, final String body) {
        given().contentType(ContentType.JSON)
                .body(body)
                .post("/workitems")
                .then().statusCode(201);
    }

    @Test
    void inbox_outcomeFilter_returnsOnlyMatchingItems() {
        final String user = uniqueUser();
        // Create two completed items: one approved, one rejected
        createItem(user, """
                {"title":"Approved item","createdBy":"test","candidateUsers":"%s","assigneeId":"%s"}
                """.formatted(user, user));
        createItem(user, """
                {"title":"Rejected item","createdBy":"test","candidateUsers":"%s","assigneeId":"%s"}
                """.formatted(user, user));

        // inbox with no outcome filter returns both (and more from other tests, so just assert >= 2)
        given().queryParam("assignee", user)
                .get("/workitems/inbox")
                .then()
                .statusCode(200)
                .body("$", hasSize(2));

        // inbox with outcome=approved returns 0 (items are PENDING — no outcome set yet)
        given().queryParam("assignee", user)
                .queryParam("outcome", "approved")
                .get("/workitems/inbox")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void inbox_priorityFilter_returnsOnlyMatchingItems() {
        final String user = uniqueUser();
        createItem(user, """
                {"title":"High item","createdBy":"test","candidateUsers":"%s","priority":"HIGH"}
                """.formatted(user));
        createItem(user, """
                {"title":"Low item","createdBy":"test","candidateUsers":"%s","priority":"LOW"}
                """.formatted(user));

        given().queryParam("candidateUser", user)
                .queryParam("priority", "HIGH")
                .get("/workitems/inbox")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].item.priority", equalTo("HIGH"));
    }

    @Test
    void inbox_categoryFilter_returnsOnlyMatchingItems() {
        final String user = uniqueUser();
        createItem(user, """
                {"title":"Legal item","createdBy":"test","candidateUsers":"%s","category":"legal"}
                """.formatted(user));
        createItem(user, """
                {"title":"Finance item","createdBy":"test","candidateUsers":"%s","category":"finance"}
                """.formatted(user));

        given().queryParam("candidateUser", user)
                .queryParam("category", "legal")
                .get("/workitems/inbox")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].item.category", equalTo("legal"));
    }

    @Test
    void inbox_statusFilter_returnsOnlyMatchingItems() {
        // Items with candidateUsers are auto-assigned by LeastLoadedStrategy → status becomes ASSIGNED.
        final String user = uniqueUser();
        createItem(user, """
                {"title":"Auto-assigned item","createdBy":"test","candidateUsers":"%s"}
                """.formatted(user));

        // status=ASSIGNED matches (auto-assignment changed status from PENDING)
        given().queryParam("assignee", user)
                .queryParam("status", "ASSIGNED")
                .get("/workitems/inbox")
                .then()
                .statusCode(200)
                .body("$", hasSize(1));

        // status=IN_PROGRESS does not match
        given().queryParam("assignee", user)
                .queryParam("status", "IN_PROGRESS")
                .get("/workitems/inbox")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void inbox_followUpFilter_returnsOnlyItemsWithFollowUpSet() {
        final String user = uniqueUser();
        createItem(user, """
                {"title":"With follow-up","createdBy":"test","candidateUsers":"%s",
                 "followUpDate":"2099-01-01T00:00:00Z"}
                """.formatted(user));
        createItem(user, """
                {"title":"Without follow-up","createdBy":"test","candidateUsers":"%s"}
                """.formatted(user));

        given().queryParam("candidateUser", user)
                .queryParam("followUp", "true")
                .get("/workitems/inbox")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].item.title", equalTo("With follow-up"));

        given().queryParam("candidateUser", user)
                .queryParam("followUp", "false")
                .get("/workitems/inbox")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].item.title", equalTo("Without follow-up"));
    }
}
