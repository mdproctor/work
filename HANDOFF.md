# HANDOFF — casehub-blocks

## Last Session

Completed the measurement-driven cognition integration epic (#261). Key achievement: validated that cognitive state presented as structured profile data measurably improves conversation quality over a pure-LLM baseline.

### Issues completed this session
- #264: Fixed map key collision in section contribution metrics (DirectivePromptSection wrapper)
- #265: Extended showcase from 4 to 8 turns
- #266: Stage 6 A/B comparison — cognition wins on depth (+1) and memory utilisation (+1)
- #267: Fixed results output path (doubled agentic-yaml prefix)
- #268: LLM-backed narrative episode synthesis
- #269: Refactored SocialAvatarCognition to delegate to CognitionCore
- #271: Configurable per-subsystem toggles (CognitionConfig)
- #272: Production directive prompt sections (DirectiveSection, opt-in)

### Key finding
Prose directives ("steer toward goals", "seek connection") made cognition WORSE — baseline won 9/8 on 3 dimensions. Removing directives and presenting cognitive state as labeled profile data flipped the result: cognition won on depth and memory utilisation. The LLM integrates character briefing + profile data emergently.

### Additional fixes
- Orchestrator LLM inference hang: MentalModel/UserModel orchestrators hung when their own invokeLlmInference() fired during tick(). Fixed by passing null agentProvider — CognitionCore.recordInteraction() handles all LLM extraction.
- DescriptorLoader rewritten to construct AgentDescriptor directly (eidos upstream API change).

## What's Next

Follow-on epic #274 — deepen cognitive advantage from 2/5 to 5/5 dimension wins:

| # | Title | Target dimension | Scale |
|---|-------|-----------------|-------|
| #275 | Goals as active cognitive objectives | Adaptiveness, Depth | M/Med |
| #276 | Personality profile as cognitive section | Character consistency | S/Med |
| #277 | Character constraints as cognitive context | Consistency, Groundedness | S/Low |
| #278 | Extended conversation length (12-16 turns) | Memory utilisation | S/Low |
| #279 | Strategy learning contribution | Adaptiveness | S/Med |

## References

| Artifact | Path |
|----------|------|
| Research findings | workspace `specs/issue-261-measurement-driven-cognition/2026-09-14-measurement-driven-cognition-findings.md` |
| Blog post | `blog/2026-09-14-mdp01-when-telling-ais-how-to-think-makes-them-worse.md` |
| Design spec | workspace `specs/issue-261-measurement-driven-cognition/2026-09-14-measurement-driven-cognition-design.md` |
| Stage results | `agentic-yaml/src/test/resources/results/` |
| CognitionCore | `blocks/src/main/java/.../social/CognitionCore.java` |
| CognitionConfig | `blocks/src/main/java/.../social/CognitionConfig.java` |
| DirectiveSection | `blocks/src/main/java/.../social/prompt/DirectiveSection.java` |
| Garden entries | `jvm/GE-20260914-6fc5ce.md` (map key collision), `tools/GE-20260914-e3cb03.md` (profile data technique) |
