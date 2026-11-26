# HANDOFF — casehub-agency (slot 223)

## Last Session

Designed and implemented #536 (composable avatar speech pipeline) end-to-end: brainstorming (8 decisions, Standard decision review), planning (6 tasks, 3 batches), implementation, Podman verification, code review, and landing on main.

**What was done this session:**
- **#536** — Composable `pipeline:` section in `avatar.yaml`. Three insertion points (post-STT, post-LLM, post-segmenter) with explicit YAML ordering. `FilterChain` preserves insertion order (unlike `CleanupConfig` which sorts by destructiveness). `TextFilterRegistry` for custom filter discovery. 9 commits landed on main, 33 files, +1254/-91. All 215 speech-ws tests + 36 avatar-yaml tests pass. Podman-verified.

**Key types added:**
- `FilterChain` — ordered text filter composition (avatar-yaml)
- `TextFilterRegistry` / `TextFilterFactory` — name-to-factory registry (avatar-yaml)
- `SpeechPipelineSpec` / `StepSpec` — YAML spec records (avatar-yaml)
- `SpeechPipelineCompiler` — compiles spec to runtime (avatar-yaml)
- `CompiledSpeechPipeline` — runtime: three `FilterChain` instances (avatar-yaml)

**Key design decisions:**
- `FilterChain` not `CleanupConfig` for user-specified chains (destructiveness sorting breaks explicit ordering)
- `pipeline:` coexists with `cleanup:` — each insertion point independently defaulted
- Post-LLM granularity is per-sentence-callback (streaming constraint)
- Registry pattern follows `SummariserRegistry` precedent
- Custom steps implement `TextFilter` from `connectors-voice-spi` — no new SPI

## Running Container (Podman)

A speech-demo container is **already running** on port 8099 with the pipeline feature.

```bash
podman ps | grep speech
podman logs --tail 20 speech-demo
open http://localhost:8099
```

**To rebuild and redeploy after code changes:**
```bash
podman stop speech-demo && podman rm speech-demo
mvn clean install -pl avatar-yaml,agentic-yaml,agentic-yaml-deployment,speech-deployment,speech-api,speech-ws -DskipTests
mvn clean package -pl speech-demo -DskipTests -Dquarkus.package.jar.type=uber-jar -Denforcer.skip=true
podman build --no-cache --layers=false -t speech-demo -f speech-demo/Containerfile speech-demo
podman run -d --name speech-demo -p 8099:8080 \
  -e CLAUDE_CODE_USE_VERTEX=1 -e CLOUD_ML_REGION=us-east5 \
  -e ANTHROPIC_VERTEX_PROJECT_ID=itpc-gcp-cp-pe-eng-claude \
  -v ~/.config/gcloud:/home/avatar/.config/gcloud:ro speech-demo
```

**Startup takes ~40s** (Kokoro + STT model provisioning).

## Immediate Next Steps

1. **#551** — Colour-coded status bar with icons — **ACTIVE** (UI work, speech-demo `index.html`)
2. **#552** — Visual reconnection indicator
3. **#553** — Integrate CorrectionHooks as pipeline step (follow-up from #536, S-scale)

## Cross-Module

- **Pre-existing build issue:** `agency` module has a compilation error (`AgencyBeans.java:377` — `AgentProvider` cannot be converted to `SessionPipeline`). Pre-existing, not from #536. Use `-pl` to skip it when building speech modules.
- **Enforcer skip:** `speech-demo` uber-jar build requires `-Denforcer.skip=true` due to a `jsonschema-module-jackson` convergence issue from the Anthropic Java SDK.

## References

- Design spec: `docs/specs/issue-536-avatar-pipeline-playbook/2026-11-26-avatar-pipeline-design.md`
- Decisions: `docs/specs/issue-536-avatar-pipeline-playbook/decisions.md`
- Consumer guide: `docs/guides/consumer-guide.md` (Pipeline Composition section)
- Blog: `docs/blog/2026-11-26-mdp02-from-flat-config-to-step-chains.md`
- Container: `speech-demo/Containerfile`
- Avatar config: `speech-demo/src/main/resources/META-INF/avatar.yaml`
