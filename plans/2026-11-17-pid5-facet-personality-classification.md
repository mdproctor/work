# PID-5 Facet Extension for Personality Classification — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #977 — PID-5 facet extension personality classification
**Issue group:** #977

**Goal:** Extend personality-aware situation classification to include PID-5 facet scores and AppraisalWeights alongside disposition axes, with CBR cache key discrimination.

**Architecture:** Extend the existing metadata-map personality pipeline in three layers: (1) enrich metadata with `pid5.*` and `appraisal.*` entries alongside existing `disposition.*` entries, (2) extend PersonalityPromptBuilder to format PID-5/appraisal info in both Path A and Path C prompts, (3) extend CbrCachingSituationDecorator to include PID-5/appraisal in SHA-256 cache key and CBR features.

**Tech Stack:** Java 21, Quarkus 3.32.2, SmallRye Config, JUnit 5, AssertJ

## Global Constraints

- All PID-5 facet scores rounded to 2 decimal places in metadata strings
- All AppraisalWeights values rounded to 2 decimal places in metadata strings
- Config property `casehub.caps.classification.pid5-facets` controls included facets; default: all 13
- Model version bump to `"haiku-pid5"` invalidates existing CBR cache entries
- No SituationClassifier SPI signature change — personality flows through `Map<String, String>` metadata

---

## Batch 1: Config and prompt builder extension

### Task 1: Add PID-5 config property and extend PersonalityPromptBuilder

**Files:**
- Modify: `caps-situation-cbr/src/main/java/io/casehub/neocortex/caps/cbr/ClassificationCbrConfig.java`
- Modify: `caps-llm-classifier/src/main/java/io/casehub/neocortex/caps/llm/PersonalityPromptBuilder.java`
- Modify: `caps-llm-classifier/src/test/java/io/casehub/neocortex/caps/llm/LlmSituationClassifierTest.java`

**Interfaces:**
- Consumes: `PersonalityFacet` constants from `io.casehub.neocortex.cognitive.index.PersonalityFacet`
- Consumes: `AppraisalWeights` record from `io.casehub.neocortex.mindmap.AppraisalWeights`
- Produces: `PersonalityPromptBuilder.pid5Suffix(Map<String, String>)` → `String`
- Produces: `PersonalityPromptBuilder.appraisalSuffix(Map<String, String>)` → `String`
- Produces: `PersonalityPromptBuilder.hasPid5(Map<String, String>)` → `boolean`
- Produces: `ClassificationCbrConfig.pid5Facets()` → `String`

- [ ] **Step 1: Add `pid5Facets` config property**

Add to `ClassificationCbrConfig`:

```java
@WithDefault("Anxiousness,Emotional Lability,Withdrawal,Impulsivity,Risk Taking,Suspiciousness,Intimacy Avoidance,Callousness,Manipulativeness,Grandiosity,Deceitfulness,Attention Seeking,Hostility")
String pid5Facets();
```

Bump default model version:

```java
@WithDefault("haiku-pid5")
String modelVersion();
```

Use `ide_edit_member` on `ClassificationCbrConfig` to add `pid5Facets()` and change `modelVersion()` default.

- [ ] **Step 2: Write failing tests for `pid5Suffix`**

Add to `LlmSituationClassifierTest`:

```java
@Test
void pid5Suffix_formatsHighLowModerate() {
    var metadata = new HashMap<String, String>();
    metadata.put("pid5.Anxiousness", "0.85");
    metadata.put("pid5.Risk Taking", "0.25");
    metadata.put("pid5.Withdrawal", "0.55");

    String suffix = PersonalityPromptBuilder.pid5Suffix(metadata);

    assertThat(suffix).contains("high Anxiousness (0.85)");
    assertThat(suffix).contains("low Risk Taking (0.25)");
    assertThat(suffix).contains("moderate Withdrawal (0.55)");
    assertThat(suffix).startsWith("\nPersonality facets:");
}

@Test
void pid5Suffix_emptyWhenNoFacets() {
    assertThat(PersonalityPromptBuilder.pid5Suffix(Map.of())).isEmpty();
    assertThat(PersonalityPromptBuilder.pid5Suffix(null)).isEmpty();
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl caps-llm-classifier -Dtest=LlmSituationClassifierTest#pid5Suffix_formatsHighLowModerate+pid5Suffix_emptyWhenNoFacets -DfailIfNoTests=false`

Expected: compilation failure — `pid5Suffix` does not exist yet.

- [ ] **Step 4: Implement `pid5Suffix` and `hasPid5`**

Add to `PersonalityPromptBuilder`:

```java
private static final String PID5_PREFIX = "pid5.";

public static boolean hasPid5(Map<String, String> metadata) {
    if (metadata == null) return false;
    return metadata.keySet().stream().anyMatch(k -> k.startsWith(PID5_PREFIX));
}

public static String pid5Suffix(Map<String, String> metadata) {
    if (metadata == null) return "";
    var facets = new StringJoiner(", ");
    metadata.entrySet().stream()
        .filter(e -> e.getKey().startsWith(PID5_PREFIX))
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> {
            String name = e.getKey().substring(PID5_PREFIX.length());
            String scoreStr = e.getValue();
            double score = Double.parseDouble(scoreStr);
            String level = score >= 0.7 ? "high" : score <= 0.3 ? "low" : "moderate";
            facets.add(level + " " + name + " (" + scoreStr + ")");
        });
    return facets.length() == 0 ? "" :
        "\nPersonality facets: " + facets + ".";
}
```

Use `ide_insert_member` to add `PID5_PREFIX`, `hasPid5`, and `pid5Suffix` after the existing `userPromptSuffix` method.

- [ ] **Step 5: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl caps-llm-classifier -Dtest=LlmSituationClassifierTest#pid5Suffix_formatsHighLowModerate+pid5Suffix_emptyWhenNoFacets -DfailIfNoTests=false`

Expected: PASS

- [ ] **Step 6: Write failing tests for `appraisalSuffix`**

Add to `LlmSituationClassifierTest`:

```java
@Test
void appraisalSuffix_formatsElevatedAndReduced() {
    var metadata = new HashMap<String, String>();
    metadata.put("appraisal.urgencyWeight", "1.45");
    metadata.put("appraisal.fearOnsetThreshold", "0.60");
    metadata.put("appraisal.relationshipWeight", "1.05"); // near-neutral, omit

    String suffix = PersonalityPromptBuilder.appraisalSuffix(metadata);

    assertThat(suffix).contains("elevated urgency");
    assertThat(suffix).contains("dampened fear onset");
    assertThat(suffix).doesNotContain("relationship"); // near-neutral omitted
    assertThat(suffix).startsWith("\nPerception biases:");
}

@Test
void appraisalSuffix_emptyWhenAllNeutral() {
    var metadata = new HashMap<String, String>();
    metadata.put("appraisal.urgencyWeight", "1.00");
    metadata.put("appraisal.fearOnsetThreshold", "1.00");

    assertThat(PersonalityPromptBuilder.appraisalSuffix(metadata)).isEmpty();
}
```

- [ ] **Step 7: Implement `appraisalSuffix`**

Add to `PersonalityPromptBuilder`:

```java
private static final String APPRAISAL_PREFIX = "appraisal.";
private static final double NEUTRAL_THRESHOLD = 0.15;

private static final Map<String, String> APPRAISAL_LABELS = Map.of(
    "urgencyWeight", "urgency sensitivity",
    "relationshipWeight", "relationship sensitivity",
    "fearOnsetThreshold", "fear onset",
    "selfStandardsStrictness", "self-standards",
    "otherStandardsStrictness", "other-standards"
);

public static String appraisalSuffix(Map<String, String> metadata) {
    if (metadata == null) return "";
    var items = new StringJoiner(", ");
    metadata.entrySet().stream()
        .filter(e -> e.getKey().startsWith(APPRAISAL_PREFIX))
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> {
            String field = e.getKey().substring(APPRAISAL_PREFIX.length());
            double val = Double.parseDouble(e.getValue());
            if (Math.abs(val - 1.0) <= NEUTRAL_THRESHOLD) return;
            String label = APPRAISAL_LABELS.getOrDefault(field, field);
            String direction = val > 1.0 ? "elevated" : "dampened";
            if (field.contains("Strictness")) {
                direction = val > 1.0 ? "strict" : "lenient";
            }
            if (field.equals("fearOnsetThreshold")) {
                direction = val > 1.0 ? "heightened" : "dampened";
            }
            items.add(direction + " " + label + " (" +
                String.format("%.2f", val) + ")");
        });
    return items.length() == 0 ? "" :
        "\nPerception biases: " + items + ".";
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl caps-llm-classifier -Dtest=LlmSituationClassifierTest#appraisalSuffix_formatsElevatedAndReduced+appraisalSuffix_emptyWhenAllNeutral -DfailIfNoTests=false`

Expected: PASS

- [ ] **Step 9: Wire PID-5 and appraisal suffixes into prompt paths**

Modify `PersonalityPromptBuilder.userPromptSuffix()` to also append `pid5Suffix()` and `appraisalSuffix()`:

```java
public static String userPromptSuffix(Map<String, String> metadata) {
    var joiner = new StringJoiner(", ");
    for (int i = 0; i < AXIS_KEYS.length; i++) {
        String val = metadata.get(DISPOSITION_PREFIX + AXIS_KEYS[i]);
        if (val != null) {
            joiner.add(val + AXIS_LABELS[i]);
        }
    }
    var sb = new StringBuilder();
    sb.append("\nCharacter disposition: ").append(joiner).append(".");
    sb.append(pid5Suffix(metadata));
    sb.append(appraisalSuffix(metadata));
    sb.append("\nClassify the situation as this character would perceive it.");
    return sb.toString();
}
```

Modify `personalitySystemPrompt()` to include PID-5 and appraisal after the tension narrative:

After `sb.append(tension.narrative()).append("\n\n");` add:

```java
String pid5 = pid5Suffix(metadata);
if (!pid5.isEmpty()) sb.append(pid5.strip()).append("\n");
String appraisal = appraisalSuffix(metadata);
if (!appraisal.isEmpty()) sb.append(appraisal.strip()).append("\n\n");
```

Use `ide_replace_member` on both methods.

- [ ] **Step 10: Write test for PID-5 in user prompt (Path A)**

Add to `LlmSituationClassifierTest`:

```java
@Test
void classify_pathA_includesPid5InUserPrompt() {
    var capturedPrompts = new ArrayList<String>();
    var pipeline = capturingPipeline(capturedPrompts,
        "{\"activations\": [{\"node\": \"reward\", \"confidence\": 0.8}]}");
    var classifier = new LlmSituationClassifier(pipeline, TOPOLOGY);

    var metadata = new HashMap<String, String>();
    metadata.put("disposition.socialOrient", "cooperative");
    metadata.put("pid5.Anxiousness", "0.85");
    metadata.put("appraisal.urgencyWeight", "1.45");

    classifier.classify("Got rewarded", metadata,
        ClassificationContext.of("t1"));

    assertThat(capturedPrompts).hasSize(1);
    assertThat(capturedPrompts.getFirst()).contains("Personality facets:");
    assertThat(capturedPrompts.getFirst()).contains("high Anxiousness");
    assertThat(capturedPrompts.getFirst()).contains("Perception biases:");
    assertThat(capturedPrompts.getFirst()).contains("elevated urgency");
}
```

- [ ] **Step 11: Run all classifier tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl caps-llm-classifier -Dtest=LlmSituationClassifierTest -DfailIfNoTests=false`

Expected: all PASS

- [ ] **Step 12: Commit**

```bash
git add caps-llm-classifier/ caps-situation-cbr/src/main/java/io/casehub/neocortex/caps/cbr/ClassificationCbrConfig.java
git commit -m "feat(#977): extend PersonalityPromptBuilder with PID-5 facets and appraisal weights

Add pid5Suffix() and appraisalSuffix() to PersonalityPromptBuilder.
Wire into both Path A (user prompt suffix) and Path C (personality system prompt).
Add pid5Facets config property. Bump model version to haiku-pid5.

Refs #977

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 2: CBR cache key and metadata enrichment

### Task 2: Extend CBR cache key and feature storage with PID-5 and appraisal

**Files:**
- Modify: `caps-situation-cbr/src/main/java/io/casehub/neocortex/caps/cbr/CbrCachingSituationDecorator.java`
- Modify: `caps-situation-cbr/src/test/java/io/casehub/neocortex/caps/cbr/CbrCachingSituationDecoratorTest.java`

**Interfaces:**
- Consumes: `pid5.*` and `appraisal.*` metadata keys (from Task 1 / Task 3)
- Produces: Extended `computeCaseId()` that hashes PID-5 + appraisal values
- Produces: Extended `addPersonalityFeatures()` that stores PID-5 + appraisal as CBR features

- [ ] **Step 1: Write failing test — same disposition, different PID-5 produces different cache key**

Add to `CbrCachingSituationDecoratorTest`:

```java
@Test
void computeCaseId_sameDisposition_differentPid5_differentIds() {
    var meta1 = new HashMap<String, String>();
    meta1.put("disposition.socialOrient", "cooperative");
    meta1.put("pid5.Anxiousness", "0.85");

    var meta2 = new HashMap<String, String>();
    meta2.put("disposition.socialOrient", "cooperative");
    meta2.put("pid5.Anxiousness", "0.20");

    String id1 = CbrCachingSituationDecorator.computeCaseId("desc", "", "", meta1);
    String id2 = CbrCachingSituationDecorator.computeCaseId("desc", "", "", meta2);

    assertThat(id1).isNotEqualTo(id2);
}

@Test
void computeCaseId_samePid5_sameId() {
    var meta1 = new HashMap<String, String>();
    meta1.put("pid5.Anxiousness", "0.85");
    meta1.put("appraisal.urgencyWeight", "1.45");

    var meta2 = new HashMap<String, String>();
    meta2.put("pid5.Anxiousness", "0.85");
    meta2.put("appraisal.urgencyWeight", "1.45");

    String id1 = CbrCachingSituationDecorator.computeCaseId("desc", "", "", meta1);
    String id2 = CbrCachingSituationDecorator.computeCaseId("desc", "", "", meta2);

    assertThat(id1).isEqualTo(id2);
}

@Test
void computeCaseId_noPid5_backwardCompatible() {
    var metaDisposition = Map.of("disposition.socialOrient", "cooperative");
    var metaDispositionWithEmptyPid5 = new HashMap<>(metaDisposition);
    // No pid5.* keys — should hash same as disposition-only

    String id1 = CbrCachingSituationDecorator.computeCaseId("desc", "ev", "ck", metaDisposition);
    String id2 = CbrCachingSituationDecorator.computeCaseId("desc", "ev", "ck", metaDispositionWithEmptyPid5);

    assertThat(id1).isEqualTo(id2);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl caps-situation-cbr -Dtest=CbrCachingSituationDecoratorTest#computeCaseId_sameDisposition_differentPid5_differentIds+computeCaseId_samePid5_sameId+computeCaseId_noPid5_backwardCompatible -DfailIfNoTests=false`

Expected: `computeCaseId_sameDisposition_differentPid5_differentIds` FAILS (PID-5 not yet in hash), others may pass trivially.

- [ ] **Step 3: Extend `computeCaseId` to include PID-5 and appraisal**

Replace the `computeCaseId(String, String, String, Map)` method body. After the disposition keys loop, add:

```java
// PID-5 facets — sorted alphabetically for deterministic hashing
if (metadata != null) {
    metadata.entrySet().stream()
        .filter(e -> e.getKey().startsWith("pid5."))
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> {
            digest.update((byte) 0);
            digest.update((e.getKey() + "=" + e.getValue())
                .getBytes(StandardCharsets.UTF_8));
        });
    // Appraisal weights — sorted alphabetically
    metadata.entrySet().stream()
        .filter(e -> e.getKey().startsWith("appraisal."))
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> {
            digest.update((byte) 0);
            digest.update((e.getKey() + "=" + e.getValue())
                .getBytes(StandardCharsets.UTF_8));
        });
}
```

Use `ide_replace_member` on the 4-arg `computeCaseId`.

- [ ] **Step 4: Extend feature storage**

Rename `addDispositionFeatures` → `addPersonalityFeatures`. After the disposition keys loop, add PID-5 and appraisal features:

```java
private static void addPersonalityFeatures(Map<String, FeatureValue> features,
                                            Map<String, String> metadata) {
    if (metadata == null) return;
    for (String key : DISPOSITION_KEYS) {
        String val = metadata.getOrDefault(key, "");
        features.put(key.replace('.', '-'), FeatureValue.string(val));
    }
    // PID-5 facets
    metadata.entrySet().stream()
        .filter(e -> e.getKey().startsWith("pid5."))
        .forEach(e -> features.put(
            e.getKey().replace('.', '-'), FeatureValue.string(e.getValue())));
    // Appraisal weights
    metadata.entrySet().stream()
        .filter(e -> e.getKey().startsWith("appraisal."))
        .forEach(e -> features.put(
            e.getKey().replace('.', '-'), FeatureValue.string(e.getValue())));
}
```

Update the call site in `storeCbr()` from `addDispositionFeatures` → `addPersonalityFeatures`.

Note: The CBR schema is static (registered at `@PostConstruct`). The PID-5 and appraisal features are stored as extra payload fields beyond the schema — Qdrant stores them as payload but they don't participate in structural CBR retrieval. This is intentional per D4 (no structural filters for numeric facets).

- [ ] **Step 5: Run all CBR decorator tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl caps-situation-cbr -Dtest=CbrCachingSituationDecoratorTest -DfailIfNoTests=false`

Expected: all PASS

- [ ] **Step 6: Commit**

```bash
git add caps-situation-cbr/
git commit -m "feat(#977): extend CBR cache key with PID-5 facets and appraisal weights

PID-5 scores and AppraisalWeights values now feed into SHA-256 cache key.
Different PID-5 profiles produce different cache entries.
Rename addDispositionFeatures → addPersonalityFeatures.

Refs #977

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 3: Extend metadata enrichment in consolidation phases

**Files:**
- Modify: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/BehavioralSynthesisPhase.java`
- Modify: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/DriveProfileDerivationPhase.java`

**Interfaces:**
- Consumes: `CognitiveDefaultsRegistry.forAgent(agentId)` → `CognitiveDefaults`
- Consumes: `CognitiveDefaults.descriptor().personalityFacets()` → `List<PersonalityFacet>`
- Consumes: `CognitiveDefaults.appraisalWeights()` → `AppraisalWeights`
- Produces: Metadata map with `pid5.*` and `appraisal.*` entries alongside `disposition.*`

- [ ] **Step 1: Extend `addDispositionMetadata` → `addPersonalityMetadata` in BehavioralSynthesisPhase**

Replace the method at line 394. Keep existing disposition logic, add PID-5 and appraisal:

```java
private Map<String, String> addPersonalityMetadata(Map<String, String> metadata, String agentId) {
    if (defaultsRegistry == null) return metadata;
    var defaults = defaultsRegistry.forAgent(agentId);
    if (defaults.isEmpty() || defaults.get().descriptor() == null) return metadata;

    var enriched = new HashMap<>(metadata);

    // Disposition axes (existing)
    var axes = defaults.get().descriptor().disposition();
    if (axes != null) {
        if (axes.socialOrient() != null) enriched.put("disposition.socialOrient", axes.socialOrient());
        if (axes.ruleFollowing() != null) enriched.put("disposition.ruleFollowing", axes.ruleFollowing());
        if (axes.riskAppetite() != null) enriched.put("disposition.riskAppetite", axes.riskAppetite());
        if (axes.autonomy() != null) enriched.put("disposition.autonomy", axes.autonomy());
        if (axes.conflictMode() != null) enriched.put("disposition.conflictMode", axes.conflictMode());
    }

    // PID-5 facets
    var facets = defaults.get().descriptor().personalityFacets();
    if (facets != null) {
        for (var facet : facets) {
            enriched.put("pid5." + facet.facet(), String.format("%.2f", facet.score()));
        }
    }

    // Appraisal weights
    var aw = defaults.get().appraisalWeights();
    if (aw != null) {
        enriched.put("appraisal.urgencyWeight", String.format("%.2f", aw.urgencyWeight()));
        enriched.put("appraisal.relationshipWeight", String.format("%.2f", aw.relationshipWeight()));
        enriched.put("appraisal.fearOnsetThreshold", String.format("%.2f", aw.fearOnsetThreshold()));
        enriched.put("appraisal.selfStandardsStrictness", String.format("%.2f", aw.selfStandardsStrictness()));
        enriched.put("appraisal.otherStandardsStrictness", String.format("%.2f", aw.otherStandardsStrictness()));
    }

    return enriched;
}
```

Update the two call sites from `addDispositionMetadata` → `addPersonalityMetadata` (lines 154 and 472).

- [ ] **Step 2: Apply same change to DriveProfileDerivationPhase**

Replace `addDispositionMetadata` at line 289 with identical `addPersonalityMetadata`. Update the call site at line 195.

- [ ] **Step 3: Build the module to verify compilation**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile -pl mindmap-intelligence -DskipTests`

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add mindmap-intelligence/
git commit -m "feat(#977): extend consolidation metadata with PID-5 facets and appraisal weights

Rename addDispositionMetadata → addPersonalityMetadata in both
BehavioralSynthesisPhase and DriveProfileDerivationPhase.
Now populates pid5.* and appraisal.* metadata keys alongside disposition.*.

Refs #977

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 3: Eval test

### Task 4: Extend PersonalityClassificationEvalTest with PID-5-differentiated profiles

**Files:**
- Modify: `caps-llm-classifier/src/test/java/io/casehub/neocortex/caps/llm/eval/PersonalityClassificationEvalTest.java`

**Interfaces:**
- Consumes: `PersonalityPromptBuilder.pid5Suffix()`, `PersonalityPromptBuilder.appraisalSuffix()` (from Task 1)
- Consumes: `LlmSituationClassifier.classify(String, Map, ClassificationContext)` (unchanged SPI)

- [ ] **Step 1: Add two PID-5-differentiated profiles**

Extend the `Profile` record to also carry PID-5 metadata. Add two new profiles sharing identical disposition axes but differing in PID-5:

```java
new Profile("anxious-cooperative", new HashMap<>(Map.ofEntries(
    Map.entry("disposition.socialOrient", "cooperative"),
    Map.entry("disposition.ruleFollowing", "strict"),
    Map.entry("disposition.riskAppetite", "conservative"),
    Map.entry("disposition.autonomy", "low"),
    Map.entry("disposition.conflictMode", "avoidant"),
    Map.entry("pid5.Anxiousness", "0.85"),
    Map.entry("pid5.Suspiciousness", "0.75"),
    Map.entry("pid5.Emotional Lability", "0.70"),
    Map.entry("pid5.Withdrawal", "0.60"),
    Map.entry("pid5.Impulsivity", "0.30"),
    Map.entry("pid5.Risk Taking", "0.20"),
    Map.entry("pid5.Intimacy Avoidance", "0.65"),
    Map.entry("appraisal.urgencyWeight", "1.45"),
    Map.entry("appraisal.fearOnsetThreshold", "1.60"),
    Map.entry("appraisal.selfStandardsStrictness", "1.60"))),
    0.0, "A"),
new Profile("calm-cooperative", new HashMap<>(Map.ofEntries(
    Map.entry("disposition.socialOrient", "cooperative"),
    Map.entry("disposition.ruleFollowing", "strict"),
    Map.entry("disposition.riskAppetite", "conservative"),
    Map.entry("disposition.autonomy", "low"),
    Map.entry("disposition.conflictMode", "avoidant"),
    Map.entry("pid5.Anxiousness", "0.20"),
    Map.entry("pid5.Suspiciousness", "0.25"),
    Map.entry("pid5.Emotional Lability", "0.30"),
    Map.entry("pid5.Withdrawal", "0.35"),
    Map.entry("pid5.Impulsivity", "0.55"),
    Map.entry("pid5.Risk Taking", "0.60"),
    Map.entry("pid5.Intimacy Avoidance", "0.30"),
    Map.entry("appraisal.urgencyWeight", "0.85"),
    Map.entry("appraisal.fearOnsetThreshold", "0.70"),
    Map.entry("appraisal.selfStandardsStrictness", "0.90"))),
    0.0, "A")
```

Use `ide_edit_member` on the `PROFILES` field to append these after the existing four profiles.

- [ ] **Step 2: Add PID-5 divergence assertion test**

Add new test method:

```java
@Test
void pid5Divergence_sameAxes_differentFacets_exceedsBaseline() {
    var anxiousActivations = activationsByProfile.get("anxious-cooperative");
    var calmActivations = activationsByProfile.get("calm-cooperative");
    assertThat(anxiousActivations).isNotNull();
    assertThat(calmActivations).isNotNull();

    double totalDistance = 0;
    int count = 0;
    for (String obsKey : anxiousActivations.keySet()) {
        double[] anxious = anxiousActivations.get(obsKey);
        double[] calm = calmActivations.get(obsKey);
        if (anxious != null && calm != null) {
            totalDistance += cosineDistance(anxious, calm);
            count++;
        }
    }
    double avgDistance = totalDistance / count;
    assertThat(avgDistance)
        .as("PID-5 divergence between anxious and calm cooperative profiles")
        .isGreaterThan(0.05);
}
```

- [ ] **Step 3: Add threat-sensitivity assertion test**

```java
@Test
void anxiousProfile_higherThreatActivation() {
    Set<String> threatNodes = Set.of("social_threat", "punishment",
        "powerlessness", "norm_violation");

    int anxiousHigherCount = 0;
    int total = 0;
    for (String obsKey : activationsByProfile.get("anxious-cooperative").keySet()) {
        double[] anxious = activationsByProfile.get("anxious-cooperative").get(obsKey);
        double[] calm = activationsByProfile.get("calm-cooperative").get(obsKey);
        if (anxious != null && calm != null &&
            hasActivationInGroup(anxious, threatNodes, topology)) {
            total++;
            double anxiousThreat = threatNodeSum(anxious, threatNodes, topology);
            double calmThreat = threatNodeSum(calm, threatNodes, topology);
            if (anxiousThreat > calmThreat) anxiousHigherCount++;
        }
    }
    if (total > 0) {
        assertThat((double) anxiousHigherCount / total)
            .as("Anxious profile should have higher threat activation in majority of observations")
            .isGreaterThanOrEqualTo(0.5);
    }
}
```

Add helper:

```java
private static double threatNodeSum(double[] vector, Set<String> nodeGroup,
                                     CapsTopology topology) {
    double sum = 0;
    var inputNodes = topology.nodes().entrySet().stream()
        .filter(e -> e.getValue().type() == NodeType.INPUT)
        .sorted(Map.Entry.comparingByKey())
        .toList();
    for (int i = 0; i < inputNodes.size(); i++) {
        if (nodeGroup.contains(inputNodes.get(i).getKey())) {
            sum += vector[i];
        }
    }
    return sum;
}
```

- [ ] **Step 4: Run eval tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl caps-llm-classifier -Dtest=PersonalityClassificationEvalTest -DfailIfNoTests=false`

Expected: PASS (this is an LLM eval — if assertions fail, adjust thresholds or investigate prompt quality. The test validates that PID-5 produces measurable divergence.)

- [ ] **Step 5: Commit**

```bash
git add caps-llm-classifier/
git commit -m "test(#977): add PID-5 divergence eval — anxious vs calm cooperative profiles

Add anxious-cooperative and calm-cooperative profiles with identical
disposition axes but different PID-5 facets. Assert activation divergence
exceeds baseline and anxious profile shows higher threat activation.

Closes #977

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## References

- [2026-11-17-pid5-facet-personality-classification-design.md] — design spec this plan implements
- `BehavioralSynthesisPhase.java:394` — existing addDispositionMetadata pattern
- `DriveProfileDerivationPhase.java:289` — parallel addDispositionMetadata
- `PersonalityPromptBuilder.java` — disposition prompt construction
- `CbrCachingSituationDecorator.java:238-256` — existing computeCaseId SHA-256
- `CbrCachingSituationDecorator.java:189-196` — existing addDispositionFeatures
- `ClassificationCbrConfig.java` — existing config mapping
- `PersonalityClassificationEvalTest.java:30-66` — existing eval profiles and observations
- `LlmSituationClassifierTest.java:214-280` — prompt assertion test patterns
- `PersonalityFacet.java` — 13 PID-5 facet constants
- `AppraisalWeights.java` — 5-field appraisal record
- GitHub #977, #941, #976, #841
