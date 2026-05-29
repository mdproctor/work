package io.casehub.work.runtime.flyway;

import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class WorkItemsMigrationCustomizerTest {

    private final WorkItemsMigrationCustomizer customizer = new WorkItemsMigrationCustomizer();

    private static final String WORK_PATH = "db/work/migration";

    @Test
    void addsWorkMigrationPathToConfiguredLocations() {
        FluentConfiguration config = new FluentConfiguration();
        config.locations("classpath:db/some-other-module/migration");

        customizer.customize(config);

        assertThat(config.getLocations())
                .extracting(Location::getPath)
                .containsExactlyInAnyOrder("db/some-other-module/migration", WORK_PATH);
    }

    @Test
    void isIdempotentWhenWorkPathAlreadyPresent() {
        FluentConfiguration config = new FluentConfiguration();
        config.locations("classpath:db/work/migration");

        customizer.customize(config);

        long count = Arrays.stream(config.getLocations())
                .map(Location::getPath)
                .filter(WORK_PATH::equals)
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void preservesExistingLocationsWhenAdding() {
        FluentConfiguration config = new FluentConfiguration();
        config.locations("classpath:db/ledger/migration");

        customizer.customize(config);

        assertThat(config.getLocations())
                .extracting(Location::getPath)
                .containsExactlyInAnyOrder("db/ledger/migration", WORK_PATH);
    }

    @Test
    void addsWorkPathAlongsideMultipleExistingLocations() {
        FluentConfiguration config = new FluentConfiguration();
        config.locations("classpath:db/ledger/migration", "classpath:db/migration");

        customizer.customize(config);

        assertThat(config.getLocations())
                .extracting(Location::getPath)
                .containsExactlyInAnyOrder("db/ledger/migration", "db/migration", WORK_PATH);
    }
}
