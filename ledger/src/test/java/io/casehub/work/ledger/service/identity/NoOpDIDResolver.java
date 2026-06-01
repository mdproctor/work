package io.casehub.work.ledger.service.identity;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.api.identity.DIDResolver;
import io.quarkus.arc.DefaultBean;

/** Test-scope no-op {@link DIDResolver} — casehub-platform JAR lacks Jandex index. */
@ApplicationScoped
@DefaultBean
public class NoOpDIDResolver implements DIDResolver {

    @Override
    public Optional<DIDDocument> resolve(final String did) {
        return Optional.empty();
    }
}
