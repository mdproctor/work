package io.casehub.work.examples.exclusion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import io.casehub.work.api.ExclusionPolicy;
import io.casehub.work.api.PolicyDecision;

/**
 * Example {@link ExclusionPolicy} implementing time-window conflict-of-interest exclusion.
 *
 * <p>The {@code excludedUsers} field encodes {@code userId:YYYY-MM-DD} entries
 * (comma-separated). A user is excluded if they appear in the list AND today is strictly
 * before their expiry date.
 *
 * <pre>{@code
 * excludedUsers = "alice:2026-08-01,bob:2026-07-15"
 * }</pre>
 *
 * <p>Parse semantics — all branches specified:
 * <ul>
 *   <li>{@code userId:YYYY-MM-DD}, today &lt; expiry → DENY with cooling-off reason</li>
 *   <li>{@code userId:YYYY-MM-DD}, today &ge; expiry → ALLOW (exclusion expired)</li>
 *   <li>{@code userId} (no colon) → DENY permanently; backward-compatible with the plain
 *       CSV format stored by {@code CommaSeparatedExclusionPolicy} and {@code TemplateExpander}</li>
 *   <li>{@code userId:not-a-date} → DENY permanently; fail-safe on unparseable dates</li>
 *   <li>{@code null}/blank → ALLOW</li>
 * </ul>
 *
 * <p><b>Note on existing data:</b> if {@code TemplateExpander} has resolved groups and
 * stored plain {@code alice,bob} CSV in {@code excludedUsers}, switching to this policy
 * treats those users as permanently excluded (no-colon → permanent exclusion). To add
 * expiry dates, update {@code excludedUsers} on affected WorkItems and templates.
 *
 * <p>This class carries no CDI annotations in the examples module to avoid overriding the
 * default for all scenarios. To activate it in a production module:
 * <pre>{@code
 * @Alternative @Priority(1) @ApplicationScoped
 * public class ExpiringExclusionPolicy implements ExclusionPolicy { ... }
 * }</pre>
 */
public class ExpiringExclusionPolicy implements ExclusionPolicy {

    private final Clock clock;

    ExpiringExclusionPolicy() {
        this(Clock.systemDefaultZone());
    }

    ExpiringExclusionPolicy(final Clock clock) {
        this.clock = clock;
    }

    @Override
    public PolicyDecision check(final String userId, final String excludedUsers) {
        if (excludedUsers == null || excludedUsers.isBlank()) {
            return PolicyDecision.ALLOW;
        }
        for (final String token : excludedUsers.split(",")) {
            final String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final int colon = trimmed.indexOf(':');
            if (colon < 0) {
                if (trimmed.equals(userId)) {
                    return PolicyDecision.deny("user '" + userId + "' in exclusion list");
                }
            } else {
                final String id = trimmed.substring(0, colon).trim();
                if (id.equals(userId)) {
                    final String dateStr = trimmed.substring(colon + 1).trim();
                    try {
                        final LocalDate expiry = LocalDate.parse(dateStr);
                        if (LocalDate.now(clock).isBefore(expiry)) {
                            return PolicyDecision.deny("user '" + userId + "' excluded until "
                                    + dateStr + " (conflict-of-interest cooling-off)");
                        }
                    } catch (final DateTimeParseException e) {
                        return PolicyDecision.deny("user '" + userId
                                + "' excluded (invalid expiry format — treating as permanent)");
                    }
                }
            }
        }
        return PolicyDecision.ALLOW;
    }
}
