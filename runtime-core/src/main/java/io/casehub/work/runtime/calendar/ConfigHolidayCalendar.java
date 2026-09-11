package io.casehub.work.runtime.calendar;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.util.Optional;

import io.casehub.work.api.spi.HolidayCalendar;

/**
 * {@link HolidayCalendar} backed by a static list of dates read from
 * {@code casehub.work.business-hours.holidays} (comma-separated {@code YYYY-MM-DD}).
 *
 * <p>
 * This class is a plain Java implementation — it is instantiated by
 * a CDI producer and is not a CDI bean itself.
 *
 * <p>
 * To use a different holiday source, either:
 * <ul>
 * <li>Configure {@code casehub.work.business-hours.holiday-ical-url} — activates
 * the iCal-backed calendar automatically.</li>
 * <li>Provide your own {@code @ApplicationScoped} CDI bean that implements
 * {@link HolidayCalendar} — it takes precedence over the default producer.</li>
 * </ul>
 */
public class ConfigHolidayCalendar implements HolidayCalendar {

    private final Set<LocalDate> holidays;

    public ConfigHolidayCalendar(final Optional<String> holidaysConfig) {
        this.holidays = holidaysConfig
                .filter(s -> !s.isBlank())
                .map(raw -> Stream.of(raw.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(LocalDate::parse)
                        .collect(Collectors.toUnmodifiableSet()))
                .orElse(Set.of());
    }

    /** Package-visible constructor for unit tests — supply holidays directly. */
    ConfigHolidayCalendar(final Set<LocalDate> holidays) {
        this.holidays = Set.copyOf(holidays);
    }

    @Override
    public boolean isHoliday(final LocalDate date, final ZoneId zone) {
        return holidays.contains(date);
    }
}
