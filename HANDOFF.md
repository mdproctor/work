# HANDOFF — 2026-08-17

## Last Session

Executed the unified container toolbar plan (5 tasks, 3 batches) and then iterated on visual/behavioral fixes. 9 commits total:

**Plan execution (5 tasks):**
1. Added `"content"` to OrganiserType, toolbar hides for content/non-free modes
2. Group always mounts toolbar, content organiser for leaf nodes
3. Eliminated ad-hoc strip button injection from sandbox Group and production backend
4. Workspace root container toolbar replaces old preset buttons
5. Removed frame-chrome arrangement dropdown, deleted old root organiser-toolbar

**Post-plan fixes (4 commits):**
- ☰ cycles through all container modes (was binary accordion toggle)
- Content toolbar ☰ delegates to frame-level view mode toggle (was calling switchContentAreaMode instead of viewModeCbs)
- ☰/+ buttons injected directly into tab strip with margin-left:auto (was in separate row below tabs)
- Content toolbar bar only inserted when no tab strip exists (was creating redundant row)

**Net result:** -145 lines from plan, three button patterns collapsed to one `[⊞] [☰] [+]` toolbar. Tab strips show `[Tab A] [Tab B] ... [☰] [+]` inline. Accordion mode shows its own toolbar. Free-layout mode shows `[⊞] [☰] [+]`.

## Known Gap — Frame-Level Free-Layout Mode

The ☰ button cycles free→tab→accordion in the toolbar, but the production backend only implements tab↔accordion at the frame level. Free-layout mode (frame's children as floating sub-frames instead of tabs/sections) is not wired.

**Root cause:** The production backend (`group-organiser-backend.ts`) doesn't use the sandbox Group for frame management. It has separate code paths for tabs, accordion, and sub-frames with no shared model. The sandbox Group correctly implements all three modes with content preservation.

**The user's direction:** A frame is a container with N entries. Each entry has key, label, content, plus per-mode metadata (position/size for free-layout, height for accordion). The mode selects the organiser — content is never destroyed. This is exactly the sandbox Group's model. The production backend needs to delegate to Group instead of maintaining parallel rendering code.

**Next step:** Migrate the production backend's frame rendering to use Group. Each frame creates a Group with its tab entries, mounts it, and mode switching calls `group.setOrganiser()`. This replaces `applyViewMode` in wire-floating-workspace.ts and `switchContentAreaMode`/`togglePaneAccordion` in the backend.

## Immediate Next Step

Implement frame-level Group migration: make `renderFrame` create a Group for each frame's tabs, delegate mode switching to `group.setOrganiser()`. Start with a focused plan.

## References

| Artifact | Path |
|----------|------|
| Spec | `specs/issue-312-workspace-compositor/2026-08-17-unified-container-toolbar-design.md` |
| Plan | `plans/2026-08-17-unified-container-toolbar.md` |
| Decisions | `specs/issue-312-workspace-compositor/decisions.md` (D15-D18) |
