# Decisions — blocks#296 Goal-Aware Cognitive Loop

## D1: Unified goal model for LLM prompt

**Choice:** MindMap cognitive goals and drive-based goals merge into a single priority-ranked list for the LLM
**Alternatives:**
- Dual streams — two separate prompt sections, LLM integrates. Simpler rendering but LLM gets no unified ranking signal.
- MindMap-primary — drives feed INTO MindMap via GoalRecognitionPhase, LLM only sees MindMap. Architecturally cleaner but requires drive-to-MindMap bridging that doesn't exist.
**Rationale:** The LLM needs one goal picture ranked by priority, not two unrelated lists it must mentally merge. Both sources carry priority signals (drive intensity, MindMap composite priority) that can be normalized into a common ranking.
**Trade-offs:** GoalPromptSection becomes more complex — must merge, dedup (via eidos-goal-name ↔ goalName), and rank two heterogeneous sources.
**Sources:** GoalPromptSection.java, GoalProposalOrchestrator.java, GoalPrioritizationPhase.java
**Exploration:** quick
**Status:** captured

## D2: Parallel orchestrators with merged prompt rendering

**Choice:** CognitiveGoalOrchestrator as a new first-class CognitionCore citizen alongside GoalProposalOrchestrator. GoalPromptSection merges both sources.
**Alternatives:**
- Extend GoalProposalOrchestrator — add MindMapStore as optional dep, query MindMap alongside drive eval. Simpler surface area but mixes two distinct responsibility domains with different tick semantics.
- CognitionTickParticipant — use addParticipant(TERMINAL, ...) extension point. No CognitionCore changes but results must reach GoalPromptSection through an indirect side-channel.
- Delegation (wrap GoalProposalOrchestrator) — single output surface, simple prompt section. But couples the two orchestrators when drive-only mode should be a natural fallback.
**Rationale:** The two orchestrators have fundamentally different tick semantics: drive goals are evaluated from internal state (pure), cognitive goals are queried from MindMap with side effects (surfacing recording, case creation trigger, revision proposals). Wrapping one around the other obscures these different responsibilities. Parallel orchestrators with merged rendering preserves clean separation.
**Trade-offs:** CognitionCore gets a new nullable field and constructor parameter. GoalPromptSection has two dependencies.
**Sources:** CognitionCore.java (lines 56-57, 172-175, 375-407), SocialAvatarCognition.java
**Exploration:** deep-analysis
**Status:** captured

## D3: Surfacing tracking via ExperienceEvent + consolidation aggregation

**Choice:** When CognitiveGoalOrchestrator surfaces goals to the LLM, it records ExperienceEvent.action() with event-type=goal-surfaced and subject=goal-node-id. A new SurfacingAggregationPhase in neocortex's consolidation pipeline aggregates these into goal node properties (surfaced-count, first-surfaced-at, last-surfaced-at). GoalAffectPhase reads the aggregated properties to compute worry.
**Alternatives:**
- Dedicated GoalSurfacingTracker SPI — explicit, testable, but adds a new SPI when ExperienceEvent already carries typed observations.
- MindMap property write from blocks — simplest, but blocks writing MindMap state crosses the neocortex ownership boundary.
- RetrievalAccessTracker pattern — designed for high-frequency access recording, but in-memory only (lost on restart) and doesn't distinguish surfacing from other access.
**Rationale:** Neocortex needs to "remember what it surfaced and when." Surfacing events flow through the existing ExperienceRecorder path (blocks already has it). Consolidation aggregates (existing pattern — see AccessFrequencyPhase, ExperienceConsolidationPhase). Node properties hold derived state (existing pattern). No new SPI needed.
**Trade-offs:** Surfacing events add volume to the experience stream (N events per surfaced goal per tick). Consolidation delay means worry updates aren't instant — but worry escalates over days, not seconds, so periodic consolidation is appropriate.
**Sources:** ExperienceRecorder (blocks), AccessFrequencyPhase (neocortex), ExperienceConsolidationPhase (neocortex)
**Exploration:** deep-analysis
**Status:** captured

## D4: Adopt OCC + ALMA as the emotion model

**Choice:** Adopt the OCC taxonomy (Ortony, Clore, Collins 1988) for emotion types, ALMA (Gebhard 2005) for OCC→PAD mapping, and Scherer's SECs for the appraisal process. Replace raw PAD writes on goal nodes with typed CognitiveEmotion records that carry both OCC type and derived PAD projection.
**Alternatives:**
- Custom taxonomy — invent our own emotion types (Worry, Frustration, Anticipation, etc.). Simpler to implement but reinvents 40 years of cognitive science, risks inconsistency, and lacks empirical grounding.
- PAD-only — continue writing raw PAD values without emotion types. Captures intensity but loses the qualitative distinction between emotions (Fear vs Distress vs Anger occupy overlapping PAD regions).
- Plutchik's wheel — 8 primary emotions with opposites. Less computationally tractable than OCC, no formal appraisal variables.
**Rationale:** OCC is the standard computational emotion framework, widely implemented in agent architectures (ALMA, WASABI, FAtiMA). It's goal-oriented by design — emotions arise from appraising events against goals, which is exactly our domain. The 22 emotion types are formally specified with appraisal variable configurations (Steunebrink et al. 2009). ALMA provides empirically grounded OCC→PAD mappings. Scherer's SECs define the sequential appraisal process.
**Trade-offs:** More complex than raw PAD — requires an appraisal layer before PAD projection. But the structure pays for itself: "Fear about the birthday" is richer than "arousal=0.6, pleasure=-0.4" and produces better LLM prompts.
**Sources:**
- [The OCC Model Revisited (Steunebrink et al. 2009)](https://people.idsia.ch/~steunebrink/Publications/KI09_OCC_revisited.pdf)
- [ALMA — A Layered Model of Affect (Gebhard 2005)](https://alma.dfki.de/papers/aamas05.pdf)
- [Component Process Model (Scherer 2001)](https://psu.pb.unizin.org/psych425/chapter/component-process-model-cpm/)
- [PAD ↔ OCC Mapping (Limbrecht et al.)](https://www.researchgate.net/publication/265439455_Pleasure_Arousal_Dominance_Mehrabian_and_Russell_revisited)
**Exploration:** deep-analysis
**Status:** captured

## D5: Personality-modulated calibration via eidos JPAF

**Choice:** Base OCC emotion intensity from appraisal variables (desirability=priority, likelihood=feasibility, proximity=dynamic urgency, coping potential=agency). Personality modulation via eidos JPAF disposition axes → OCEAN → PAD baseline (Mehrabian 1996 / Gebhard 2004 mapping). CognitiveDefaults already provides per-agent config.
**Alternatives:**
- Fixed intensity — same emotional intensity for all agents given the same situation. Simpler but produces flat, personality-less responses.
- Manual per-agent tuning — hand-calibrate emotion thresholds per agent profile. Doesn't scale.
**Rationale:** The Limbrecht et al. study showed PAD Pleasure maps reliably across individuals but Arousal and Dominance are personality-dependent. Individual calibration via personality is the empirically correct approach. We already have the personality pipeline (eidos JPAF → CognitiveDerivationEngine → CognitiveDefaults → PersonalityWeights).
**Trade-offs:** Requires the personality modulation pipeline to be active. Agents without personality profiles fall back to neutral baseline (no modulation).
**Sources:** CognitiveDerivationEngine.java, CognitiveDefaults.java, PersonalityWeights.java, MoodBaseline.java
**Exploration:** quick
**Status:** captured

## D6: ONNX-trainable appraisal (future, not in #296 scope)

**Choice:** Park the ONNX training path as a follow-up. Start with heuristic OCC appraisal rules in #296, design the appraisal interface so an ONNX model can replace the heuristics later without structural changes.
**Alternatives:**
- Train ONNX model now — use ISEAR + GoEmotions + EmoBank datasets. More empirically grounded but requires ML pipeline work that's out of scope for #296.
**Rationale:** The datasets exist (ISEAR: 7,666 situations with appraisal data across 37 countries; GoEmotions: 58k fine-grained labels; EmoBank: 10k sentences with VAD ratings). The inference infrastructure exists (neocortex inference-tasks). But training and validating the model is a separate workstream. The interface should be clean enough that swapping heuristics for a model is a drop-in replacement.
**Trade-offs:** Heuristic appraisal rules may not produce perfectly calibrated emotional responses. But they're transparent, testable, and can be iterated quickly.
**Sources:** ISEAR dataset (Scherer, UNIGE CISA), inference-tasks module (neocortex)
**Exploration:** quick
**Status:** captured
