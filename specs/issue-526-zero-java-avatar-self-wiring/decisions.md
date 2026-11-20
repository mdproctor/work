# Decisions — #526 Zero-Java Avatar Self-Wiring

## D1: Module extraction strategy

**Choice:** New `speech-deployment` module — dedicated Quarkus deployment module with a narrow dependency set
**Alternatives:**
- Sub-module restructure — keep in agentic-yaml-deployment but break the agency-core dependency. Risk of re-introducing the dependency later.
**Rationale:** Clean module boundary. Consumer adds speech-deployment + speech-ws + agent-claude. No agency-core on classpath at all.
**Trade-offs:** New module to maintain. Build matrix grows by one.
**Sources:** agentic-yaml-deployment/pom.xml, speech-demo/pom.xml (exclusion pattern)
**Exploration:** quick
**Status:** captured

## D2: Avatar type location

**Choice:** Stay in agentic-yaml, speech-deployment depends on agentic-yaml with `<exclusion>` for agency-core
**Alternatives:**
- Move to speech-api or new speech-runtime — cleanest graph but breaks existing consumers
- Copy into speech-deployment — duplicates create maintenance burden
**Rationale:** Proven pattern — speech-demo already uses this exclusion successfully. No type relocation, no consumer breakage.
**Trade-offs:** Maven exclusions are fragile if someone adds a new transitive path to agency-core later.
**Sources:** speech-demo/pom.xml lines 25-33 (existing exclusion), AvatarCompiler.java imports (no agency-core references)
**Exploration:** quick
**Status:** captured

## D3: LLM provider integration

**Choice:** Consumer adds `casehub-platform-agent-claude` dependency; `ClaudeAgentProvider` produced automatically from `application.properties`
**Alternatives:**
- AgentProvider as injected SPI — more flexible but requires consumer wiring
- Built-in Vertex AI with config — locks to one provider
**Rationale:** Zero Java. Platform already handles credential resolution, model selection, concurrency gates. Consumer sets API key in application.properties.
**Trade-offs:** Locks the "zero-code" path to Claude/Anthropic. Other providers need a Java bean.
**Sources:** platform/agent-claude/ClaudeBeans.java, platform/agent-config/AgentConfigBeans.java
**Exploration:** quick
**Status:** captured

## D4: Frontend component packaging

**Choice:** ES module bundle (`avatar-component.mjs`) with integrated controls (voice selector, LLM picker, speed slider, animation picker)
**Alternatives:**
- Web Component (Custom Element) — strongest encapsulation but shadow DOM constraints and boilerplate
- Template HTML — simplest but not reusable
**Rationale:** Matches zero-code goal. Consumer imports one module, calls one init function. Controls included by default.
**Trade-offs:** Styling is limited to what the component exposes. Consumer-specific UI requires overriding the built-in controls.
**Sources:** speech-demo/src/main/resources/META-INF/resources/index.html (750 lines of inline JS)
**Exploration:** quick
**Status:** captured
