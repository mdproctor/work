# Decisions — #449 Avatar Reusable Mosaic Component

## D1: Embedding model

**Choice:** Web Component (`<casehub-avatar>` custom element)
**Alternatives:**
- iframe — consistent with legacy pages components, but mosaic uses web components natively
- Both — maximum portability, but doubles the integration surface
**Rationale:** Mosaic composes UIs from web components, not iframes. Iframes are legacy for third-party content.
**Trade-offs:** Not usable in non-web-component hosts without a wrapper
**Sources:** casehub-mosaic CLAUDE.md ("Framework-agnostic Web Components where possible"), existing mosaic-avatar package
**Exploration:** quick
**Status:** captured

## D2: Rendering scope

**Choice:** Full rendering — component owns the 3D scene (Three.js + TalkingHead)
**Alternatives:**
- Headless + events — maximum flexibility but every host must bring its own 3D renderer
- Layered (core + renderer) — two packages, more maintenance
**Rationale:** Self-contained drop-in is the primary use case. The existing `casehub-avatar` already does this.
**Trade-offs:** Bundles Three.js dependency
**Sources:** casehub-mosaic/packages/avatar/src/casehub-avatar.ts
**Exploration:** quick
**Status:** captured

## D3: Target platform

**Choice:** casehub-mosaic (not casehub-pages)
**Alternatives:**
- casehub-pages — has iframe-based component system, but that's legacy
**Rationale:** Mosaic is the UI composition layer for CaseHub applications. Pages provides runtime primitives; mosaic provides domain components.
**Trade-offs:** None — this is where mosaic components belong
**Sources:** casehub-mosaic CLAUDE.md, user clarification
**Exploration:** quick
**Status:** captured

## D4: Cross-tile state sharing

**Choice:** Shared AvatarSession service — singleton that sub-components discover and subscribe to
**Alternatives:**
- Event bus on document — loose coupling but no shared object, harder to manage lifecycle
- Coordinator element — DOM-traversal discovery, fragile across shadow DOM boundaries
**Rationale:** Sub-components in different mosaic tiles need shared WebSocket connection and state. A service singleton provides clean lifecycle management and typed subscriptions.
**Trade-offs:** Components must discover the service (via context protocol or registration)
**Sources:** casehub-mosaic/packages/avatar/src/avatar-ws-controller.ts (current coupled approach)
**Exploration:** quick
**Status:** captured

## D5: Config delivery to frontend

**Choice:** REST endpoint (`/api/avatar/config`) serves compiled avatar config as JSON
**Alternatives:**
- WebSocket handshake — couples config to connection lifecycle
- Host provides via attributes — splits config responsibility between backend and frontend
**Rationale:** Single source of truth. Frontend AvatarSession fetches config on init. Backend compiles YAML once, serves to any consumer.
**Trade-offs:** Extra HTTP call on init
**Sources:** casehub-mosaic/packages/avatar/src/casehub-avatar-panel.ts (already polls /api/models/status)
**Exploration:** quick
**Status:** captured

## D6: Voice configuration

**Choice:** YAML declares available voices explicitly
**Alternatives:**
- Runtime auto-discovery — register what's on classpath, YAML only sets default
- Hybrid — YAML overlays runtime discovery
**Rationale:** Explicit control per deployment. Consumer declares which TTS engines and voices are enabled. The compiled config produces the TtsModelRegistry.
**Trade-offs:** Consumer must list voices (can't just "get everything available")
**Sources:** speech-demo/SpeechProducers.java (current hardcoded approach)
**Exploration:** quick
**Status:** captured

## D7: Issue scope

**Choice:** Both repos (agency + mosaic) in one issue
**Alternatives:**
- Split into two issues — can be developed independently but feature only works end-to-end
**Rationale:** The feature is end-to-end: agency provides YAML config, mosaic consumes it. Ship as one coherent change.
**Trade-offs:** Cross-repo coordination required
**Sources:** User decision
**Exploration:** quick
**Status:** captured

## D8: Controls component

**Choice:** Single `<casehub-avatar-controls>` component, data-driven from AvatarSession config
**Alternatives:**
- Individual control components (`<casehub-llm-picker>`, `<casehub-voice-picker>`) — maximum flexibility but more wiring
**Rationale:** Simpler. One component renders LLM picker, voice picker, speed from the config served by the backend. Available options come from YAML, not hardcoded.
**Trade-offs:** Less granular composition of individual controls
**Sources:** casehub-mosaic/packages/avatar/src/casehub-avatar-panel.ts (current hardcoded controls)
**Exploration:** quick
**Status:** captured

## D9: LLM routing

**Choice:** Avatar YAML references an eidos agent by agentId. LLM routing uses platform's AgentProvider + AgentSessionConfig (model/modelQuery/modelChain). No LLM config in the avatar YAML.
**Alternatives:**
- Bare model string in avatar YAML — bypasses platform routing
- Inline LLM config — duplicates what eidos already provides
**Rationale:** The platform already has a full LLM routing stack (eidos AgentDescriptor -> AgentProvider -> AgentSessionConfig with model/modelQuery/modelChain). The avatar YAML should reference an agent identity, not duplicate routing config.
**Trade-offs:** Requires an eidos agent descriptor to exist for the avatar to have an LLM
**Sources:** eidos-api AgentDescriptor.java, platform-agent-api AgentSessionConfig.java, AgentProvider.java
**Exploration:** quick
**Status:** captured

## D10: Avatar YAML scope — physical layer only

**Choice:** Avatar YAML configures only the physical rendering and speech pipeline: 3D model, TTS voice, STT engine, cleanup, camera. No cognition, no tenant-id, no LLM config. The avatar is a "body" for the agent; the agent (eidos) brings the "mind."
**Alternatives:**
- Cognition toggle in avatar YAML — but cognition belongs to the agent, not the avatar
- Tenant-id in avatar YAML — but that's infrastructure resolved at runtime by the platform
**Rationale:** Clean separation: avatar = body (rendering + speech), agent = mind (LLM + personality + cognition). The avatar pipeline discovers cognition from the agent automatically.
**Trade-offs:** Avatar alone does nothing without an agent — but that's the correct dependency
**Sources:** speech-ws SpeechWebSocket.java (avatarCognition resolved via CDI Instance), eidos AgentDescriptor (voice, briefing, providerConfigs)
**Exploration:** quick
**Status:** captured
