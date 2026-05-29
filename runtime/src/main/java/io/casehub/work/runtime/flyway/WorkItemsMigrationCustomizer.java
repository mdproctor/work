package io.casehub.work.runtime.flyway;

import io.quarkus.flyway.FlywayConfigurationCustomizer;
import jakarta.enterprise.context.ApplicationScoped;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds {@code classpath:db/work/migration} to Flyway's configured locations,
 * additively and idempotently.
 *
 * <p><b>Non-Quarkus use:</b> effective — adds the location at JVM startup.
 *
 * <p><b>Quarkus limitation:</b> silently no-op for location scanning. Quarkus
 * pre-registers migration file lists at build time ({@code FlywayProcessor.build()});
 * {@code QuarkusPathLocationScanner} only serves files from that pre-registered list.
 * Runtime location additions via this customizer are ignored by the scanner. Quarkus
 * consumers must configure {@code quarkus.flyway.locations=classpath:db/work/migration}
 * explicitly in their application.properties.
 */
@ApplicationScoped
public class WorkItemsMigrationCustomizer implements FlywayConfigurationCustomizer {

    @Override
    public void customize(FluentConfiguration configuration) {
        Location target = new Location("classpath:db/work/migration");
        List<Location> locations = new ArrayList<>(List.of(configuration.getLocations()));
        // Location.getPath() strips the classpath: prefix — comparison is prefix-agnostic
        // e.g. new Location("classpath:db/work/migration").getPath() == "db/work/migration"
        if (locations.stream().noneMatch(l -> l.getPath().equals(target.getPath()))) {
            locations.add(target);
            configuration.locations(locations.toArray(new Location[0]));
        }
    }
}
