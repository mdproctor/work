# Structured RoutingFeatureExtractor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #33 — structured RoutingFeatureExtractor for domain-specific routing
**Issue group:** #33

**Goal:** Enable CBR routing to use structured features instead of text-only
similarity, by adding a blocks integration test proving the SPI contract and
implementing concrete extractors in AML and clinical repos.

**Architecture:** The `RoutingFeatureExtractor` SPI is complete in blocks.
Domain repos implement it via `@ApplicationScoped` (displaces the
`@DefaultBean TextOnlyFeatureExtractor`). Each extractor navigates the
domain's `caseContext` JsonNode to produce typed features for `CbrQuery`.
No new abstractions in blocks.

**Tech Stack:** Java 21, Jackson (JsonNode), JUnit 5, Mockito, AssertJ.
CDI `@DefaultBean` displacement (Pattern C).

## Global Constraints

- blocks has no CDI container in tests — plain JUnit 5 + Mockito
- Domain extractors are pure Java — unit tests with constructed JsonNodes
- `AgentRoutingContext` is a 4-field record: `(UUID caseId, String capabilityName, JsonNode caseContext, String tenancyId)`
- AML `caseContext` keys come from `AmlPriorContext.toContextMap()`: `knownHighRisk`, `entityRiskCount`, `networkCount`, `patternCount`
- AML entity resolution output appears under `/entityResolution/` (entityType, riskScore) — absent before first capability completes
- Clinical grade is stored as `CtcaeGrade` enum name string (e.g. `"GRADE_3"`) — must parse numeric suffix
- Clinical patient context keys from `ClinicalPatientContext.toContextMap()`: `hasPriorGrade3OrAbove`, `hasPriorEscalation`, `aeCount`
- CDI: `@ApplicationScoped` on domain extractor displaces `@DefaultBean` automatically — no `@Alternative` needed
- Domain repos (aml, clinical) need `casehub-blocks` dependency added to pom.xml

---

### Task 1: Blocks integration test — structured features through CBR query path

**Files:**
- Modify: `src/test/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategyTest.java`

**Interfaces:**
- Consumes: `RoutingFeatureExtractor`, `CbrAgentRoutingStrategy`, `CbrQuery`, `AgentRoutingContext`
- Produces: Test proving structured features and null problem text flow correctly through the CBR query pipeline

- [ ] **Step 1: Write failing test — structured features flow to CbrQuery**

Add to the existing `CbrAgentRoutingStrategyTest` class (not in a nested class):

```java
@Test
void structuredFeaturesFlowToCbrQuery() {
    var customExtractor = mock(RoutingFeatureExtractor.class);
    when(customExtractor.extractFeatures(any())).thenReturn(Map.of(
            "riskScore", 0.87,
            "entityType", "PEP",
            "knownHighRisk", true));
    when(customExtractor.extractProblem(any())).thenReturn("entityType=PEP, riskScore=0.87");

    var planTrace = new PlanTrace("binding-a", "analysis", "agent-a", "SUCCESS", 1, Map.of());
    var cbrCase = new PlanCbrCase("problem", "solution", "RESOLVED", 0.9,
            Map.of("riskScore", 0.85, "entityType", "PEP", "knownHighRisk", true),
            List.of(planTrace));
    when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
            .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.92)));

    var strategy = new CbrAgentRoutingStrategy(
            10, 0.5, presentInstance(cbrStore), presentInstance(graphQuery),
            absentInstance(), absentInstance(), absentInstance(),
            customExtractor);

    var result = strategy.select(context("analysis"),
            List.of(candidate("agent-a"))).await().indefinitely();

    assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
    assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");

    var queryCaptor = org.mockito.ArgumentCaptor.forClass(CbrQuery.class);
    verify(cbrStore).retrieveSimilar(queryCaptor.capture(), eq(CbrCase.class));
    var query = queryCaptor.getValue();
    assertThat(query.features())
            .containsEntry("riskScore", 0.87)
            .containsEntry("entityType", "PEP")
            .containsEntry("knownHighRisk", true);
    assertThat(query.problem()).isEqualTo("entityType=PEP, riskScore=0.87");
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `mvn --batch-mode test -pl . -Dtest=CbrAgentRoutingStrategyTest#structuredFeaturesFlowToCbrQuery`
Expected: PASS (the production code already supports this — the test documents the contract)

- [ ] **Step 3: Write test — null problem text omits withProblem**

```java
@Test
void nullProblemTextProducesQueryWithoutProblem() {
    var customExtractor = mock(RoutingFeatureExtractor.class);
    when(customExtractor.extractFeatures(any())).thenReturn(Map.of("grade", 3));
    when(customExtractor.extractProblem(any())).thenReturn(null);
    when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
            .thenReturn(List.of());
    when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
            .thenReturn(List.of("agent-a"));

    var strategy = new CbrAgentRoutingStrategy(
            10, 0.5, presentInstance(cbrStore), presentInstance(graphQuery),
            absentInstance(), absentInstance(), absentInstance(),
            customExtractor);

    strategy.select(context("analysis"),
            List.of(candidate("agent-a"))).await().indefinitely();

    var queryCaptor = org.mockito.ArgumentCaptor.forClass(CbrQuery.class);
    verify(cbrStore).retrieveSimilar(queryCaptor.capture(), eq(CbrCase.class));
    assertThat(queryCaptor.getValue().problem()).isNull();
    assertThat(queryCaptor.getValue().features()).containsEntry("grade", 3);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl . -Dtest=CbrAgentRoutingStrategyTest#nullProblemTextProducesQueryWithoutProblem`
Expected: PASS

- [ ] **Step 5: Run full test suite**

Run: `mvn --batch-mode test`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add src/test/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategyTest.java
git commit -m "test(#33): structured feature extraction contract tests

Refs casehubio/blocks#33"
```

---

### Task 2: AML feature extractor

**Files:**
- Create: `aml/app/src/main/java/io/casehub/aml/routing/AmlRoutingFeatureExtractor.java`
- Create: `aml/app/src/test/java/io/casehub/aml/routing/AmlRoutingFeatureExtractorTest.java`
- Modify: `aml/app/pom.xml` (add casehub-blocks dependency)

**Interfaces:**
- Consumes: `RoutingFeatureExtractor` (from blocks), `AgentRoutingContext` (from engine-api)
- Produces: `AmlRoutingFeatureExtractor` — CDI bean that displaces `TextOnlyFeatureExtractor`

**Pre-condition:** Verify aml repo is on main and clean before making changes.

- [ ] **Step 1: Add casehub-blocks dependency to aml/app/pom.xml**

Add to the `<dependencies>` section:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-blocks</artifactId>
</dependency>
```

Version is managed by casehub-parent. Verify blocks is installed to local Maven
repo first: `mvn --batch-mode install -DskipTests` in the blocks project.

- [ ] **Step 2: Write failing test — full feature vector (post entity-resolution)**

```java
package io.casehub.aml.routing;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.api.spi.routing.AgentRoutingContext;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AmlRoutingFeatureExtractorTest {

    private final AmlRoutingFeatureExtractor extractor = new AmlRoutingFeatureExtractor();

    private AgentRoutingContext contextWith(ObjectNode caseContext) {
        return new AgentRoutingContext(UUID.randomUUID(), "pattern-analysis", caseContext, "tenant-1");
    }

    @Test
    void extractFeatures_fullContext_returnsAllFeatures() {
        var node = JsonNodeFactory.instance.objectNode();
        var prior = node.putObject("priorEntityContext");
        prior.put("knownHighRisk", true);
        prior.put("entityRiskCount", 5);
        prior.put("networkCount", 3);
        prior.put("patternCount", 2);
        var er = node.putObject("entityResolution");
        er.put("entityType", "PEP");
        er.put("riskScore", 0.87);

        var features = extractor.extractFeatures(contextWith(node));

        assertThat(features)
                .containsEntry("knownHighRisk", true)
                .containsEntry("entityRiskCount", 5)
                .containsEntry("networkCount", 3)
                .containsEntry("patternCount", 2)
                .containsEntry("entityType", "PEP")
                .containsEntry("riskScore", 0.87)
                .hasSize(6);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn --batch-mode test -f aml/app/pom.xml -Dtest=AmlRoutingFeatureExtractorTest#extractFeatures_fullContext_returnsAllFeatures`
Expected: FAIL — `AmlRoutingFeatureExtractor` class does not exist

- [ ] **Step 4: Write AmlRoutingFeatureExtractor**

```java
package io.casehub.aml.routing;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.blocks.routing.agent.RoutingFeatureExtractor;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class AmlRoutingFeatureExtractor implements RoutingFeatureExtractor {

    @Override
    public Map<String, Object> extractFeatures(AgentRoutingContext context) {
        JsonNode root = context.caseContext();
        if (root == null || root.isNull()) {
            return Map.of();
        }

        Map<String, Object> features = new LinkedHashMap<>();
        JsonNode prior = root.path("priorEntityContext");
        if (!prior.isMissingNode()) {
            putIfPresent(features, "knownHighRisk", prior, JsonNode::asBoolean);
            putIntIfPresent(features, "entityRiskCount", prior);
            putIntIfPresent(features, "networkCount", prior);
            putIntIfPresent(features, "patternCount", prior);
        }

        JsonNode er = root.path("entityResolution");
        if (!er.isMissingNode()) {
            putStringIfPresent(features, "entityType", er);
            putDoubleIfPresent(features, "riskScore", er);
        }

        return Map.copyOf(features);
    }

    @Override
    public @Nullable String extractProblem(AgentRoutingContext context) {
        JsonNode root = context.caseContext();
        if (root == null || root.isNull()) {
            return null;
        }

        JsonNode prior = root.path("priorEntityContext");
        JsonNode er = root.path("entityResolution");

        if (!er.isMissingNode() && er.has("entityType")) {
            return String.format("entityType=%s, riskScore=%s, %d prior entity risks",
                    er.path("entityType").asText(),
                    er.has("riskScore") ? String.valueOf(er.path("riskScore").asDouble()) : "unknown",
                    prior.path("entityRiskCount").asInt(0));
        }

        if (!prior.isMissingNode() && prior.has("entityRiskCount")) {
            var sb = new StringBuilder();
            sb.append(prior.path("entityRiskCount").asInt(0)).append(" prior entity risks");
            if (prior.path("networkCount").asInt(0) > 0) {
                sb.append(", ").append(prior.path("networkCount").asInt(0)).append(" network patterns");
            }
            if (prior.path("knownHighRisk").asBoolean(false)) {
                sb.append(", known high risk");
            }
            return sb.toString();
        }

        return null;
    }

    private static void putIfPresent(Map<String, Object> map, String key,
                                     JsonNode parent, java.util.function.Function<JsonNode, Object> converter) {
        JsonNode child = parent.path(key);
        if (!child.isMissingNode()) {
            map.put(key, converter.apply(child));
        }
    }

    private static void putIntIfPresent(Map<String, Object> map, String key, JsonNode parent) {
        JsonNode child = parent.path(key);
        if (!child.isMissingNode() && child.isInt()) {
            map.put(key, child.asInt());
        }
    }

    private static void putDoubleIfPresent(Map<String, Object> map, String key, JsonNode parent) {
        JsonNode child = parent.path(key);
        if (!child.isMissingNode() && child.isNumber()) {
            map.put(key, child.asDouble());
        }
    }

    private static void putStringIfPresent(Map<String, Object> map, String key, JsonNode parent) {
        JsonNode child = parent.path(key);
        if (!child.isMissingNode() && child.isTextual()) {
            map.put(key, child.asText());
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn --batch-mode test -f aml/app/pom.xml -Dtest=AmlRoutingFeatureExtractorTest#extractFeatures_fullContext_returnsAllFeatures`
Expected: PASS

- [ ] **Step 6: Write test — sparse features (pre entity-resolution)**

Add to `AmlRoutingFeatureExtractorTest`:

```java
@Test
void extractFeatures_sparseContext_returnsPriorFeaturesOnly() {
    var node = JsonNodeFactory.instance.objectNode();
    var prior = node.putObject("priorEntityContext");
    prior.put("knownHighRisk", false);
    prior.put("entityRiskCount", 2);
    prior.put("networkCount", 0);
    prior.put("patternCount", 1);
    // no entityResolution node

    var features = extractor.extractFeatures(contextWith(node));

    assertThat(features)
            .containsEntry("knownHighRisk", false)
            .containsEntry("entityRiskCount", 2)
            .containsEntry("networkCount", 0)
            .containsEntry("patternCount", 1)
            .doesNotContainKey("entityType")
            .doesNotContainKey("riskScore")
            .hasSize(4);
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `mvn --batch-mode test -f aml/app/pom.xml -Dtest=AmlRoutingFeatureExtractorTest#extractFeatures_sparseContext_returnsPriorFeaturesOnly`
Expected: PASS

- [ ] **Step 8: Write test — null/empty context**

```java
@Test
void extractFeatures_nullNode_returnsEmpty() {
    var ctx = new AgentRoutingContext(UUID.randomUUID(), "analysis", NullNode.instance, "t");
    assertThat(extractor.extractFeatures(ctx)).isEmpty();
}

@Test
void extractProblem_nullNode_returnsNull() {
    var ctx = new AgentRoutingContext(UUID.randomUUID(), "analysis", NullNode.instance, "t");
    assertThat(extractor.extractProblem(ctx)).isNull();
}
```

- [ ] **Step 9: Run tests to verify they pass**

Run: `mvn --batch-mode test -f aml/app/pom.xml -Dtest=AmlRoutingFeatureExtractorTest`
Expected: PASS

- [ ] **Step 10: Write test — problem text with entity data**

```java
@Test
void extractProblem_withEntityResolution_includesEntityInfo() {
    var node = JsonNodeFactory.instance.objectNode();
    var prior = node.putObject("priorEntityContext");
    prior.put("entityRiskCount", 3);
    var er = node.putObject("entityResolution");
    er.put("entityType", "PEP");
    er.put("riskScore", 0.87);

    var problem = extractor.extractProblem(contextWith(node));

    assertThat(problem).isEqualTo("entityType=PEP, riskScore=0.87, 3 prior entity risks");
}
```

- [ ] **Step 11: Write test — problem text without entity data (sparse)**

```java
@Test
void extractProblem_withoutEntityResolution_usesCountsAndFlags() {
    var node = JsonNodeFactory.instance.objectNode();
    var prior = node.putObject("priorEntityContext");
    prior.put("knownHighRisk", true);
    prior.put("entityRiskCount", 5);
    prior.put("networkCount", 2);
    prior.put("patternCount", 0);

    var problem = extractor.extractProblem(contextWith(node));

    assertThat(problem).isEqualTo("5 prior entity risks, 2 network patterns, known high risk");
}
```

- [ ] **Step 12: Run all AML extractor tests**

Run: `mvn --batch-mode test -f aml/app/pom.xml -Dtest=AmlRoutingFeatureExtractorTest`
Expected: All 6 tests PASS

- [ ] **Step 13: Commit**

```bash
git -C /path/to/aml add app/pom.xml \
    app/src/main/java/io/casehub/aml/routing/AmlRoutingFeatureExtractor.java \
    app/src/test/java/io/casehub/aml/routing/AmlRoutingFeatureExtractorTest.java
git -C /path/to/aml commit -m "feat(#33): AML structured feature extractor for CBR routing

Implements RoutingFeatureExtractor with domain-specific features from
AmlPriorContext (always available) and entity resolution output (after
first capability). Displaces TextOnlyFeatureExtractor via @DefaultBean
pattern.

Refs casehubio/blocks#33"
```

---

### Task 3: Clinical feature extractor

**Files:**
- Create: `clinical/runtime/src/main/java/io/casehub/clinical/routing/ClinicalRoutingFeatureExtractor.java`
- Create: `clinical/runtime/src/test/java/io/casehub/clinical/routing/ClinicalRoutingFeatureExtractorTest.java`
- Modify: `clinical/runtime/pom.xml` (add casehub-blocks dependency)

**Interfaces:**
- Consumes: `RoutingFeatureExtractor` (from blocks), `AgentRoutingContext` (from engine-api)
- Produces: `ClinicalRoutingFeatureExtractor` — CDI bean that displaces `TextOnlyFeatureExtractor`

**Pre-condition:** Verify clinical repo is on main and clean before making changes.

- [ ] **Step 1: Add casehub-blocks dependency to clinical/runtime/pom.xml**

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-blocks</artifactId>
</dependency>
```

- [ ] **Step 2: Write failing test — full feature vector**

```java
package io.casehub.clinical.routing;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.api.spi.routing.AgentRoutingContext;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalRoutingFeatureExtractorTest {

    private final ClinicalRoutingFeatureExtractor extractor = new ClinicalRoutingFeatureExtractor();

    private AgentRoutingContext contextWith(ObjectNode caseContext) {
        return new AgentRoutingContext(UUID.randomUUID(), "safety-monitoring", caseContext, "tenant-1");
    }

    @Test
    void extractFeatures_fullContext_returnsAllFeatures() {
        var node = JsonNodeFactory.instance.objectNode();
        node.put("grade", "GRADE_3");
        node.put("unexpected", true);
        node.put("suspected", false);
        node.put("siteId", "site-london-01");
        var patient = node.putObject("patientContext");
        patient.put("hasPriorGrade3OrAbove", true);
        patient.put("hasPriorEscalation", false);
        patient.put("aeCount", 4);

        var features = extractor.extractFeatures(contextWith(node));

        assertThat(features)
                .containsEntry("ctcaeGrade", 3)
                .containsEntry("unexpected", true)
                .containsEntry("suspected", false)
                .containsEntry("siteId", "site-london-01")
                .containsEntry("hasPriorGrade3OrAbove", true)
                .containsEntry("hasPriorEscalation", false)
                .containsEntry("aeCount", 4)
                .hasSize(7);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn --batch-mode test -f clinical/runtime/pom.xml -Dtest=ClinicalRoutingFeatureExtractorTest#extractFeatures_fullContext_returnsAllFeatures`
Expected: FAIL — class does not exist

- [ ] **Step 4: Write ClinicalRoutingFeatureExtractor**

```java
package io.casehub.clinical.routing;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.blocks.routing.agent.RoutingFeatureExtractor;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ClinicalRoutingFeatureExtractor implements RoutingFeatureExtractor {

    private static final Pattern GRADE_PATTERN = Pattern.compile("GRADE_(\\d+)");

    @Override
    public Map<String, Object> extractFeatures(AgentRoutingContext context) {
        JsonNode root = context.caseContext();
        if (root == null || root.isNull()) {
            return Map.of();
        }

        Map<String, Object> features = new LinkedHashMap<>();

        int grade = parseGrade(root.path("grade"));
        if (grade > 0) {
            features.put("ctcaeGrade", grade);
        }

        putBoolIfPresent(features, "unexpected", root);
        putBoolIfPresent(features, "suspected", root);
        putStringIfPresent(features, "siteId", root);

        JsonNode patient = root.path("patientContext");
        if (!patient.isMissingNode()) {
            putBoolIfPresent(features, "hasPriorGrade3OrAbove", patient);
            putBoolIfPresent(features, "hasPriorEscalation", patient);
            putIntIfPresent(features, "aeCount", patient);
        }

        return Map.copyOf(features);
    }

    @Override
    public @Nullable String extractProblem(AgentRoutingContext context) {
        JsonNode root = context.caseContext();
        if (root == null || root.isNull()) {
            return null;
        }

        int grade = parseGrade(root.path("grade"));
        if (grade <= 0) {
            return null;
        }

        boolean unexpected = root.path("unexpected").asBoolean(false);
        int aeCount = root.path("patientContext").path("aeCount").asInt(0);

        return String.format("Grade %d AE, %s, patient has %d prior AEs",
                grade, unexpected ? "unexpected" : "expected", aeCount);
    }

    static int parseGrade(JsonNode gradeNode) {
        if (gradeNode.isMissingNode() || !gradeNode.isTextual()) {
            return 0;
        }
        Matcher m = GRADE_PATTERN.matcher(gradeNode.asText());
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static void putBoolIfPresent(Map<String, Object> map, String key, JsonNode parent) {
        JsonNode child = parent.path(key);
        if (!child.isMissingNode() && child.isBoolean()) {
            map.put(key, child.asBoolean());
        }
    }

    private static void putIntIfPresent(Map<String, Object> map, String key, JsonNode parent) {
        JsonNode child = parent.path(key);
        if (!child.isMissingNode() && child.isInt()) {
            map.put(key, child.asInt());
        }
    }

    private static void putStringIfPresent(Map<String, Object> map, String key, JsonNode parent) {
        JsonNode child = parent.path(key);
        if (!child.isMissingNode() && child.isTextual()) {
            map.put(key, child.asText());
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn --batch-mode test -f clinical/runtime/pom.xml -Dtest=ClinicalRoutingFeatureExtractorTest#extractFeatures_fullContext_returnsAllFeatures`
Expected: PASS

- [ ] **Step 6: Write test — grade parsing edge cases**

```java
@Test
void extractFeatures_invalidGradeString_omitsGrade() {
    var node = JsonNodeFactory.instance.objectNode();
    node.put("grade", "UNKNOWN");

    var features = extractor.extractFeatures(contextWith(node));

    assertThat(features).doesNotContainKey("ctcaeGrade");
}

@Test
void parseGrade_allValidGrades() {
    assertThat(ClinicalRoutingFeatureExtractor.parseGrade(
            JsonNodeFactory.instance.textNode("GRADE_1"))).isEqualTo(1);
    assertThat(ClinicalRoutingFeatureExtractor.parseGrade(
            JsonNodeFactory.instance.textNode("GRADE_5"))).isEqualTo(5);
}

@Test
void parseGrade_missingNode_returnsZero() {
    assertThat(ClinicalRoutingFeatureExtractor.parseGrade(
            com.fasterxml.jackson.databind.node.MissingNode.getInstance())).isZero();
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn --batch-mode test -f clinical/runtime/pom.xml -Dtest=ClinicalRoutingFeatureExtractorTest`
Expected: PASS

- [ ] **Step 8: Write test — null/empty context**

```java
@Test
void extractFeatures_nullNode_returnsEmpty() {
    var ctx = new AgentRoutingContext(UUID.randomUUID(), "safety-monitoring", NullNode.instance, "t");
    assertThat(extractor.extractFeatures(ctx)).isEmpty();
}

@Test
void extractProblem_nullNode_returnsNull() {
    var ctx = new AgentRoutingContext(UUID.randomUUID(), "safety-monitoring", NullNode.instance, "t");
    assertThat(extractor.extractProblem(ctx)).isNull();
}
```

- [ ] **Step 9: Write test — problem text formatting**

```java
@Test
void extractProblem_unexpectedGrade3_formatsCorrectly() {
    var node = JsonNodeFactory.instance.objectNode();
    node.put("grade", "GRADE_3");
    node.put("unexpected", true);
    var patient = node.putObject("patientContext");
    patient.put("aeCount", 2);

    assertThat(extractor.extractProblem(contextWith(node)))
            .isEqualTo("Grade 3 AE, unexpected, patient has 2 prior AEs");
}

@Test
void extractProblem_expectedGrade4_formatsCorrectly() {
    var node = JsonNodeFactory.instance.objectNode();
    node.put("grade", "GRADE_4");
    node.put("unexpected", false);
    var patient = node.putObject("patientContext");
    patient.put("aeCount", 0);

    assertThat(extractor.extractProblem(contextWith(node)))
            .isEqualTo("Grade 4 AE, expected, patient has 0 prior AEs");
}
```

- [ ] **Step 10: Run all clinical extractor tests**

Run: `mvn --batch-mode test -f clinical/runtime/pom.xml -Dtest=ClinicalRoutingFeatureExtractorTest`
Expected: All tests PASS

- [ ] **Step 11: Commit**

```bash
git -C /path/to/clinical add runtime/pom.xml \
    runtime/src/main/java/io/casehub/clinical/routing/ClinicalRoutingFeatureExtractor.java \
    runtime/src/test/java/io/casehub/clinical/routing/ClinicalRoutingFeatureExtractorTest.java
git -C /path/to/clinical commit -m "feat(#33): clinical structured feature extractor for CBR routing

Implements RoutingFeatureExtractor with CTCAE grade parsing (enum name
to ordinal), AE flags, and patient escalation history. Displaces
TextOnlyFeatureExtractor via @DefaultBean pattern.

Refs casehubio/blocks#33"
```
