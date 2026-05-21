package io.casehub.work.runtime.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Persistent cursor tracking the last-assigned index for a round-robin candidate pool.
 *
 * <p>
 * Pool identity is the SHA-256 of sorted candidate IDs (64 hex characters).
 * {@code lastIndex} starts at -1; the first {@code acquireNext()} returns 0.
 * {@code version} provides OCC via JPA {@code @Version}.
 * {@code lastAccessed} is stamped on every {@code acquireNext()} call for TTL-based GC.
 */
@Entity
@Table(name = "routing_cursor")
public class RoutingCursor extends PanacheEntityBase {

    @Id
    @Column(name = "pool_hash", length = 64, nullable = false, updatable = false)
    public String poolHash;

    @Column(name = "last_index", nullable = false)
    public int lastIndex = -1;

    @Version
    @Column(name = "version", nullable = false)
    public int version;

    @Column(name = "last_accessed", nullable = false)
    public Instant lastAccessed = Instant.now();

    public RoutingCursor() {}

    public RoutingCursor(final String poolHash) {
        this.poolHash = poolHash;
        this.lastIndex = -1;
        this.lastAccessed = Instant.now();
    }
}
