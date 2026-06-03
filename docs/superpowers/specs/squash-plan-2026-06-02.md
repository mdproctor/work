# Squash Plan — issue-235-sxs-sweep
**Date:** 2026-06-02
**Range:** upstream/main..issue-235-sxs-sweep
**Mode:** Flat compaction (no PR merge commits)

---

## Already Clean — 0 commits

All commits are in action groups.

---

## Group 1 — LabelDefinition.path migration (#189)
*2 commits → 1*

✅ KEEP `1e8e9fc` feat(queues): migrate LabelDefinition.path to casehub-platform-api Path
> Absorbed: `8297a12` docs: update runtime test count to 763 after #189

> **Result:** 1 commit. *(message adequate — unchanged)*

---

## Group 2 — findByCallerRef for engine HumanTask restart (parent#56)
*2 commits → 1 (MERGE)*
**Final message:** `feat: add WorkItemService.findByCallerRef + indexed store override — engine HumanTask restart recovery, avoids table scan in production`

⏱ 5 minutes apart — confirmed genuinely distinct concerns (SPI vs store layer).

| Commit | Action | Curated result |
|--------|--------|----------------|
| `57b21be` feat: add WorkItemService.findByCallerRef(String) for engine HumanTask restart recovery | ✅ KEEP | *(see Final message above)* |
| `79f3bc6` refine: WorkItemStore.findByCallerRef default method + JPA indexed override (avoids table scan in production) | 🔀 MERGE ↑ | *(unified — service API + store layer are two halves of the same capability; indexed override context added to message)* |

> **Result:** 1 commit.

---

## Group 3 — defaultPayload deep-merge (#175)
*2 commits → 1*

✅ KEEP `a32d2c9` feat(runtime): deep-merge defaultPayload with payloadOverride in WorkItemTemplateService
> Absorbed: `208b0a9` docs: update runtime test count to 770 after #175

> **Result:** 1 commit. *(message adequate — unchanged)*

---

## Group 4 — excludedGroups conflict-of-interest exclusion (#184)
*2 commits → 1*

✅ KEEP `6b009c3` feat(runtime): excludedGroups — group-level conflict-of-interest exclusion on WorkItemTemplate
> Absorbed: `68eca37` docs: update runtime test count to 786 after #184

> **Result:** 1 commit. *(message adequate — unchanged)*

---

## Group 5 — ExpiringExclusionPolicy example + spec/ADR promotion (#185)
*3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `8739b40` feat(examples): ExpiringExclusionPolicy — time-window exclusion example with 12 tests | ✅ KEEP | *(message adequate — unchanged)* |
| `c329e64` docs: promote specs from issue-235-sxs-sweep | 🔽 SQUASH ↑ | *(absorbed — workspace workflow artifact; spec content already in project)* |
| `08c0e4e` feat: promote adr/specs from issue-235-sxs-sweep  Refs #235 | 🔽 SQUASH ↑ | *(absorbed — ADR and INDEX content preserved in tree; promotion housekeeping)* |

> **Result:** 1 commit. ADR `0005-group-membership-snapshot-at-workitem-creation.md` content
> is preserved in the squashed commit's tree.

---

## AFTER — what `git log --oneline` will show

```
11  commits (original)
-6  absorbed by squash/merge
─────────────────────────────
 5  commits — no content lost
```

Sample (post-squash, most recent first):
```
feat(examples): ExpiringExclusionPolicy — time-window exclusion example with 12 tests
feat(runtime): excludedGroups — group-level conflict-of-interest exclusion on WorkItemTemplate
feat(runtime): deep-merge defaultPayload with payloadOverride in WorkItemTemplateService
feat: add WorkItemService.findByCallerRef + indexed store override — ...
feat(queues): migrate LabelDefinition.path to casehub-platform-api Path
```
