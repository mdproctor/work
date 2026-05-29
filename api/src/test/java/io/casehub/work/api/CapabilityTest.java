package io.casehub.work.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CapabilityTest {

    @ParameterizedTest
    @ValueSource(strings = {"legal-review", "a", "legal", "contract-analysis", "review-3", "x2"})
    void of_acceptsValidKebabCase(String id) {
        assertThat(Capability.of(id).id()).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Legal-Review", "legal_review", "legal-", "a--b", "3legal", "-legal"})
    void of_rejectsInvalidFormat(String id) {
        assertThatThrownBy(() -> Capability.of(id))
                .isInstanceOf(MalformedCapabilityException.class)
                .hasMessageContaining(id);
    }

    @Test
    void of_rejectsEmptyString() {
        assertThatThrownBy(() -> Capability.of(""))
                .isInstanceOf(MalformedCapabilityException.class);
    }

    @Test
    void of_rejectsNull() {
        assertThatThrownBy(() -> Capability.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equalCapabilities_areEqual() {
        assertThat(Capability.of("legal-review")).isEqualTo(Capability.of("legal-review"));
    }

    @Test
    void exceptionCarriesBadValue() {
        MalformedCapabilityException ex = null;
        try { Capability.of("Bad_Value"); }
        catch (MalformedCapabilityException e) { ex = e; }
        assertThat(ex).isNotNull();
        assertThat(ex.badValues()).containsExactly("Bad_Value");
    }
}
