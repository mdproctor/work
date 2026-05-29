package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.Capability;
import io.casehub.work.api.MalformedCapabilityException;
import io.casehub.work.api.WorkCapabilities;

class CapabilityParserTest {

    // --- parse() strict mode ---

    @Test
    void parse_returnsEmptySet_forNullInput() {
        assertThat(CapabilityParser.parse(null)).isEmpty();
    }

    @Test
    void parse_returnsEmptySet_forBlankInput() {
        assertThat(CapabilityParser.parse("   ")).isEmpty();
    }

    @Test
    void parse_trimsWhitespaceAroundTokens() {
        assertThat(CapabilityParser.parse("  legal-review , contract-analysis  "))
                .containsExactlyInAnyOrder(
                        WorkCapabilities.LEGAL_REVIEW, WorkCapabilities.CONTRACT_ANALYSIS);
    }

    @Test
    void parse_skipsEmptyTokensFromDoubleCommas() {
        assertThat(CapabilityParser.parse("legal-review,,contract-analysis"))
                .containsExactlyInAnyOrder(
                        WorkCapabilities.LEGAL_REVIEW, WorkCapabilities.CONTRACT_ANALYSIS);
    }

    @Test
    void parse_throws_onMalformedToken() {
        assertThatThrownBy(() -> CapabilityParser.parse("legal-review,legal_review"))
                .isInstanceOf(MalformedCapabilityException.class)
                .satisfies(ex -> assertThat(((MalformedCapabilityException) ex).badValues())
                        .containsExactly("legal_review"));
    }

    @Test
    void parse_returnsSingleElement() {
        assertThat(CapabilityParser.parse("legal-review"))
                .containsExactly(WorkCapabilities.LEGAL_REVIEW);
    }

    // --- parseLenient() lenient mode ---

    @Test
    void parseLenient_returnsEmptySet_forNullInput() {
        assertThat(CapabilityParser.parseLenient(null)).isEmpty();
    }

    @Test
    void parseLenient_skipsMalformedToken_andRetainsValid() {
        assertThat(CapabilityParser.parseLenient("legal-review,legal_review,contract-analysis"))
                .containsExactlyInAnyOrder(
                        WorkCapabilities.LEGAL_REVIEW, WorkCapabilities.CONTRACT_ANALYSIS);
    }

    @Test
    void parseLenient_returnsEmptySet_whenAllTokensMalformed() {
        assertThat(CapabilityParser.parseLenient("Legal_Review,BAD")).isEmpty();
    }
}
