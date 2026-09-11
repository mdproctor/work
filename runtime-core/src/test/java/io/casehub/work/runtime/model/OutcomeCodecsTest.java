package io.casehub.work.runtime.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.Outcome;

class OutcomeCodecsTest {

    // ── decodePermittedOutcomes — new object-array format ───────────────────

    @Test
    void decode_newFormat_objectArray_decodesOutcomes() {
        final String json = "[{\"name\":\"approved\",\"displayName\":\"Approved\",\"condition\":null},"
                + "{\"name\":\"rejected\",\"displayName\":\"Rejected\",\"condition\":\"reason != null\"}]";

        final List<Outcome> result = OutcomeCodecs.decodePermittedOutcomes(json);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("approved");
        assertThat(result.get(0).displayName()).isEqualTo("Approved");
        assertThat(result.get(0).condition()).isNull();
        assertThat(result.get(1).name()).isEqualTo("rejected");
        assertThat(result.get(1).condition()).isEqualTo("reason != null");
    }

    @Test
    void decode_newFormat_objectWithCondition_preservesCondition() {
        final String json = "[{\"name\":\"escalate\",\"displayName\":null,\"condition\":\"workItem.priority.name() == 'URGENT'\"}]";

        final List<Outcome> result = OutcomeCodecs.decodePermittedOutcomes(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).condition()).isEqualTo("workItem.priority.name() == 'URGENT'");
    }

    // ── decodePermittedOutcomes — legacy string-array format ─────────────────

    @Test
    void decode_legacyFormat_stringArray_wrapsToOutcomes() {
        final String json = "[\"approved\",\"rejected\"]";

        final List<Outcome> result = OutcomeCodecs.decodePermittedOutcomes(json);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("approved");
        assertThat(result.get(0).displayName()).isNull();
        assertThat(result.get(0).condition()).isNull();
        assertThat(result.get(1).name()).isEqualTo("rejected");
    }

    @Test
    void decode_legacyFormat_singleName_wrapsToOutcome() {
        final List<Outcome> result = OutcomeCodecs.decodePermittedOutcomes("[\"only-option\"]");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("only-option");
    }

    // ── decodePermittedOutcomes — whitespace-formatted JSON ──────────────────

    @Test
    void decode_whitespaceFormatted_objectArray_stillDecodes() {
        // Regression: old startsWith("[{") would fail on whitespace between tokens
        final String json = "[ {\"name\":\"approved\",\"displayName\":null,\"condition\":null} ]";

        final List<Outcome> result = OutcomeCodecs.decodePermittedOutcomes(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("approved");
    }

    @Test
    void decode_whitespaceFormatted_stringArray_stillDecodes() {
        final String json = "[ \"approved\", \"rejected\" ]";

        final List<Outcome> result = OutcomeCodecs.decodePermittedOutcomes(json);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("approved");
    }

    // ── decodePermittedOutcomes — empty array ─────────────────────────────────

    @Test
    void decode_emptyArray_returnsEmptyList() {
        final List<Outcome> result = OutcomeCodecs.decodePermittedOutcomes("[]");
        assertThat(result).isEmpty();
    }

    // ── decodePermittedOutcomes — null / blank ─────────────────────────────────

    @Test
    void decode_null_returnsNull() {
        assertThat(OutcomeCodecs.decodePermittedOutcomes(null)).isNull();
    }

    @Test
    void decode_blank_returnsNull() {
        assertThat(OutcomeCodecs.decodePermittedOutcomes("   ")).isNull();
    }

    // ── decodePermittedOutcomes — non-array / corrupt JSON ────────────────────

    @Test
    void decode_corruptJson_returnsNull() {
        assertThat(OutcomeCodecs.decodePermittedOutcomes("not valid {{{{")).isNull();
    }

    @Test
    void decode_nonArrayJson_jsonObject_returnsNull() {
        // Valid JSON but not an array — should not throw ClassCastException
        assertThat(OutcomeCodecs.decodePermittedOutcomes("{\"name\":\"approved\"}")).isNull();
    }

    @Test
    void decode_nonArrayJson_scalar_returnsNull() {
        assertThat(OutcomeCodecs.decodePermittedOutcomes("\"just-a-string\"")).isNull();
    }

    // ── Round-trip: encodeOutcomes → decodePermittedOutcomes ─────────────────

    @Test
    void roundTrip_encodeDecodePreservesAllFields() {
        final List<Outcome> original = List.of(
                new Outcome("approved", "Approved", null),
                new Outcome("escalate", "Escalate", "workItem.priority.name() == 'URGENT'"),
                new Outcome("rejected", null, "reason != null")
        );

        final String encoded = OutcomeCodecs.encodeOutcomes(original);
        final List<Outcome> decoded = OutcomeCodecs.decodePermittedOutcomes(encoded);

        assertThat(decoded).isEqualTo(original);
    }

    // ── encodeOutcomes ────────────────────────────────────────────────────────

    @Test
    void encode_null_returnsNull() {
        assertThat(OutcomeCodecs.encodeOutcomes(null)).isNull();
    }

    @Test
    void encode_emptyList_returnsNull() {
        assertThat(OutcomeCodecs.encodeOutcomes(List.of())).isNull();
    }
}
