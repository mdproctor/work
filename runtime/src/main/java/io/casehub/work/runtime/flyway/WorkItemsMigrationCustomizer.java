package io.casehub.work.runtime.flyway;

import io.quarkus.flyway.FlywayConfigurationCustomizer;
import jakarta.enterprise.context.ApplicationScoped;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class WorkItemsMigrationCustomizer implements FlywayConfigurationCustomizer {

    @Override
    public void customize(FluentConfiguration configuration) {
        Location target = new Location("classpath:db/work/migration");
        List<Location> locations = new ArrayList<>(Arrays.asList(configuration.getLocations()));
        // Location.getPath() strips the classpath: prefix — comparison is prefix-agnostic
        // e.g. new Location("classpath:db/work/migration").getPath() == "db/work/migration"
        if (locations.stream().noneMatch(l -> l.getPath().equals(target.getPath()))) {
            locations.add(target);
            configuration.locations(locations.toArray(new Location[0]));
        }
    }
}
