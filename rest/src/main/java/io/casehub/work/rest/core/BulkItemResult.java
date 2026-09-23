package io.casehub.work.rest.core;

public record BulkItemResult(String id, String status, String error) {

    public static BulkItemResult ok(String id) {
        return new BulkItemResult(id, "ok", null);
    }

    public static BulkItemResult error(String id, String message) {
        return new BulkItemResult(id, "error", message);
    }
}
