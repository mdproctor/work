package io.casehub.work.ledger.service.identity;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.AgentCredentialValidator;
import io.casehub.platform.api.identity.CredentialValidationResult;
import io.quarkus.arc.DefaultBean;

/** Test-scope no-op {@link AgentCredentialValidator} — casehub-platform JAR lacks Jandex index. */
@ApplicationScoped
@DefaultBean
public class NoOpAgentCredentialValidator implements AgentCredentialValidator {

    @Override
    public Optional<CredentialValidationResult> validate(final String actorId, final String did) {
        return Optional.empty();
    }
}
