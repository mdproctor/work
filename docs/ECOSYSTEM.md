# Ecosystem Conventions

Cross-project conventions for all casehubio projects.

## Quarkus Version

All projects use `3.32.2`. When bumping, bump all projects together.

## GitHub Packages — Dependency Resolution

Add to `pom.xml` `<repositories>`:

```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/casehubio/*</url>
  <snapshots><enabled>true</enabled></snapshots>
</repository>
```

CI must use `server-id: github` + `GITHUB_TOKEN` in `actions/setup-java`.

## Cross-Project SNAPSHOT Versions

`casehub-ledger` and `casehub-work` modules are `0.2-SNAPSHOT` resolved from GitHub Packages. Declare in `pom.xml` properties and `<dependencyManagement>` — no hardcoded versions in submodule poms.

## SNAPSHOT API Drift

CI pulls the latest `casehub-ledger:0.2-SNAPSHOT` from GitHub Packages; local builds use the cached jar. When `casehub-ledger` adds new abstract methods to `LedgerEntryRepository`, CI breaks but local passes silently. Before concluding a build is stable, refresh the local cache:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn install -DskipTests -f ~/claude/casehub/ledger/pom.xml
```

## Git Workflow — Fork Model

```
origin   → personal fork   (git remote get-url origin)
upstream → casehubio       (git remote get-url upstream)
```

Before starting any branch: `git fetch upstream && git rebase upstream/main` to sync local main with casehubio. At work-end: rebase the branch onto local main, push to `origin main`. PRs to `upstream` are created separately, on demand — never automatically at work-end.
