# Flyway Migration Conventions

Read this before adding any database migration to this project.

---

## Version Range Ownership

Each module owns its own version range. Flyway enforces uniqueness across all modules loaded into the same application — a duplicate version number causes a startup failure.

| Range | Module |
|---|---|
| V1–V999 | `runtime` (sequential, at V30); `casehub-work-ai` also occupies V14 filling a deliberate gap — no more optional-module migrations in this range |
| V2000–V2999 | `casehub-work-queues` and `casehub-work-ledger` (shared 2000s block) |
| V3000–V3999 | `casehub-work-notifications` |
| V4000–V4999 | `casehub-work-ai` |
| V5000–V5999 | `casehub-work-issue-tracker` |
| V6000+ | next new optional module |

**Rule for a new module:** take the next free thousand above the highest used — currently V5000.

---

## Branch-Level V-Number Reservation (Concurrent Epics)

Two epic branches that both add runtime migrations independently pick the same next V number, causing a startup failure at merge time (even on a fresh install).

At epic start:
1. Fetch all remote epic branches:
   ```bash
   git -C <project> fetch --all
   git -C <project> log --remotes="*/epic-*" --name-only --format="" \
     | grep -oP "(?<=V)\d+(?=__)" | sort -n | tail -1
   ```
2. Take the maximum across `main` AND all branches. Claim the next slot.
3. Record it as `flyway-next-v: <N>` in `design/.meta`.
4. Re-verify at merge time — if another epic merged first and took your number, renumber before merging.

Renumbering is always safe before the first production deployment.

---

## casehub-ledger Prerequisites

`casehub-work-ledger` depends on `io.casehub:casehub-ledger:0.2-SNAPSHOT` — a sibling project at `~/claude/casehub/ledger/`. If the build fails with "Could not find artifact", install it first:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn install -DskipTests -f ~/claude/casehub/ledger/pom.xml
```

`casehub-ledger#95` moved base ledger migrations (`ledger_entry`, `ledger_attestation`, `actor_trust_score` tables) from `classpath:db/migration` to `classpath:db/ledger/migration`. Any module consuming `casehub-ledger` or `casehub-work-ledger` must declare both paths in test `application.properties`:

```properties
quarkus.flyway.locations=db/migration,db/ledger/migration
```

Missing the ledger path causes `FlywayMigrateException: Table "LEDGER_ENTRY" not found` on V2001+ migrations.

### Configurable datasource

`casehub-ledger` 0.2-SNAPSHOT introduced `@LedgerPersistenceUnit` and `LedgerEntityManagerProducer` — the ledger can pick a named persistence unit. If a consuming app uses only a named datasource (no CDI `@Default` EntityManager), add:

```properties
quarkus.ledger.datasource=mydb
```

Omit this property when a default datasource is present. Introduced in casehub-ledger commit `1f8ca69`, issue #46.

---

## Format Check

CI runs `mvn -Dno-format` to skip the enforced formatter. Run `mvn` locally to apply formatting before committing.
