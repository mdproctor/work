package io.casehub.work.ledger.service.identity;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.ActorDIDProvider;
import io.quarkus.arc.DefaultBean;

/** Test-scope no-op {@link ActorDIDProvider} — casehub-platform JAR lacks Jandex index. */
@ApplicationScoped
@DefaultBean
public class NoOpActorDIDProvider implements ActorDIDProvider {

    @Override
    public Optional<String> didFor(final String actorId) {
        return Optional.empty();
    }
}
