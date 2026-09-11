package io.casehub.work.runtime.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for ConfigHolidayCalendar — no Quarkus, no CDI.
 */
class ConfigHolidayCalendarTest {

    @Test
    void isHoliday_trueForConfiguredDate() {
        final ConfigHolidayCalendar cal = new ConfigHolidayCalendar(Optional.of("2026-12-25,2026-01-01"));
        assertThat(cal.isHoliday(LocalDate.of(2026, 12, 25), ZoneId.of("UTC"))).isTrue();
        assertThat(cal.isHoliday(LocalDate.of(2026, 1, 1), ZoneId.of("UTC"))).isTrue();
    }

    @Test
    void isHoliday_falseForNonConfiguredDate() {
        final ConfigHolidayCalendar cal = new ConfigHolidayCalendar(Optional.of("2026-12-25"));
        assertThat(cal.isHoliday(LocalDate.of(2026, 12, 26), ZoneId.of("UTC"))).isFalse();
    }

    @Test
    void isHoliday_falseWhenNoHolidaysConfigured() {
        final ConfigHolidayCalendar cal = new ConfigHolidayCalendar(Optional.empty());
        assertThat(cal.isHoliday(LocalDate.of(2026, 12, 25), ZoneId.of("UTC"))).isFalse();
    }

    @Test
    void isHoliday_handlesWhitespaceInList() {
        final ConfigHolidayCalendar cal = new ConfigHolidayCalendar(Optional.of("2026-12-25 , 2026-01-01"));
        assertThat(cal.isHoliday(LocalDate.of(2026, 12, 25), ZoneId.of("UTC"))).isTrue();
        assertThat(cal.isHoliday(LocalDate.of(2026, 1, 1), ZoneId.of("UTC"))).isTrue();
    }

    @Test
    void isHoliday_zoneIgnored_dateMatchesRegardlessOfZone() {
        final ConfigHolidayCalendar cal = new ConfigHolidayCalendar(Optional.of("2026-12-25"));
        // Config-backed calendar is date-only — zone does not affect result
        assertThat(cal.isHoliday(LocalDate.of(2026, 12, 25), ZoneId.of("America/New_York"))).isTrue();
        assertThat(cal.isHoliday(LocalDate.of(2026, 12, 25), ZoneId.of("Asia/Tokyo"))).isTrue();
    }

}
