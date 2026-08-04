# Structured Progress Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #62 — feat: integrate structured progress into conversation rendering
**Issue group:** #62

**Goal:** Add structured progress as the fourth supplementary render-time
input on `RenderContext`, with a pluggable `ProgressRenderer` SPI and
built-in `DefaultProgressRenderer` handling percentage/count/step shapes.

**Architecture:** Follows the established `RenderContext` pattern used by
reactions, commonGround, and convergence. New `ProgressRenderer` SPI with
`DefaultProgressRenderer` switches on `ProgressInstance.shapeType()`.
`ConversationRenderer` delegates per-instance rendering to the SPI and
renders progress overlays per topic section.

**Tech Stack:** Java 21 records, Jackson `JsonNode` (from work-api),
JUnit 5 + Mockito

## Global Constraints

- `casehub-work-progress-api` at provided scope — consumers bring runtime
- `showProgress` defaults to `false` — backward compatible
- No changes to `ConversationState`, `ConversationProjection`, `ConversationFold`
- Null-safe rendering — null `state()`/`definition()` falls back to status-only

---

### Task 1: Add progress-api dependency and RenderContext migration

**Files:**
- Modify: `pom.xml:26-29`
- Modify: `src/main/java/io/casehub/blocks/conversation/RenderContext.java`
- Modify: `src/test/java/io/casehub/blocks/conversation/ConversationRendererTest.java` (6 call sites)
- Modify: `src/test/java/io/casehub/blocks/conversation/CommonGroundConvergenceIntegrationTest.java:80`

**Interfaces:**
- Produces: `RenderContext.progress()` → `Map<String, List<ProgressInstance>>`, `RenderContext.withProgress()` factory, updated `EMPTY` constant

- [ ] **Step 1: Add `casehub-work-progress-api` dependency to pom.xml**

Add after the existing `casehub-work-api` dependency block:

```xml
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-work-progress-api</artifactId>
      <scope>provided</scope>
    </dependency>
```

- [ ] **Step 2: Write the failing test for RenderContext.withProgress()**

In a new test file `src/test/java/io/casehub/blocks/conversation/RenderContextTest.java`:

```java
package io.casehub.blocks.conversation;

import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RenderContextTest {

    @Test
    void empty_hasEmptyProgressMap() {
        assertThat(RenderContext.EMPTY.progress()).isEmpty();
    }

    @Test
    void withProgress_createsContextWithProgress() {
        var pi = testProgressInstance("percentage", 
                mapper().createObjectNode().put("value", 50));
        var progress = Map.of("review", List.of(pi));

        var ctx = RenderContext.withProgress(progress);

        assertThat(ctx.progress()).isEqualTo(progress);
        assertThat(ctx.reactions()).isEmpty();
        assertThat(ctx.commonGround()).isNull();
        assertThat(ctx.convergence()).isNull();
    }

    @Test
    void withReactions_hasEmptyProgressMap() {
        var ctx = RenderContext.withReactions(Map.of());
        assertThat(ctx.progress()).isEmpty();
    }

    private static ProgressInstance testProgressInstance(String shapeType, 
            com.fasterxml.jackson.databind.JsonNode state) {
        return new ProgressInstance(
                UUID.randomUUID(), "tenant-1", "WORK_ITEM", "scope-1",
                null, null, shapeType, null, state,
                ProgressStatus.ACTIVE, null,
                Instant.now(), Instant.now());
    }

    private static com.fasterxml.jackson.databind.ObjectMapper mapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -pl . -Dtest=RenderContextTest -DfailIfNoTests=false --batch-mode`
Expected: compilation failure — `RenderContext` has no `progress` field yet

- [ ] **Step 4: Update RenderContext to add progress field**

Replace the entire `RenderContext.java` with:

```java
package io.casehub.blocks.conversation;

import io.casehub.qhorus.api.message.ReactionGroup;
import io.casehub.work.progress.ProgressInstance;

import java.util.List;
import java.util.Map;

public record RenderContext(
        Map<Long, List<ReactionGroup>> reactions,
        CommonGroundState commonGround,
        ConvergenceSignal convergence,
        Map<String, List<ProgressInstance>> progress) {

    public static final RenderContext EMPTY =
            new RenderContext(Map.of(), null, null, Map.of());

    public static RenderContext withReactions(Map<Long, List<ReactionGroup>> reactions) {
        return new RenderContext(reactions, null, null, Map.of());
    }

    public static RenderContext withProgress(Map<String, List<ProgressInstance>> progress) {
        return new RenderContext(Map.of(), null, null, progress);
    }
}
```

- [ ] **Step 5: Fix existing test call sites**

Six direct constructor calls in tests need the fourth `Map.of()` argument:

In `ConversationRendererTest.java`, update each `new RenderContext(Map.of(), ...)` call:
- Line 575: `new RenderContext(Map.of(), cg, null)` → `new RenderContext(Map.of(), cg, null, Map.of())`
- Line 591: same change
- Line 606: same change
- Line 618: `new RenderContext(Map.of(), null, signal)` → `new RenderContext(Map.of(), null, signal, Map.of())`
- Line 635: `new RenderContext(Map.of(), cg, null)` → `new RenderContext(Map.of(), cg, null, Map.of())`

In `CommonGroundConvergenceIntegrationTest.java`:
- Line 80: `new RenderContext(Map.of(), cg, signal)` → `new RenderContext(Map.of(), cg, signal, Map.of())`

- [ ] **Step 6: Run all tests to verify migration**

Run: `mvn test --batch-mode`
Expected: all existing tests pass, `RenderContextTest` passes

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add pom.xml src/main/java/io/casehub/blocks/conversation/RenderContext.java src/test/java/io/casehub/blocks/conversation/RenderContextTest.java src/test/java/io/casehub/blocks/conversation/ConversationRendererTest.java src/test/java/io/casehub/blocks/conversation/CommonGroundConvergenceIntegrationTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#62): add progress-api dependency and RenderContext progress field"
```

---

### Task 2: ProgressRenderer SPI and DefaultProgressRenderer

**Files:**
- Create: `src/main/java/io/casehub/blocks/conversation/ProgressRenderer.java`
- Create: `src/main/java/io/casehub/blocks/conversation/DefaultProgressRenderer.java`
- Create: `src/test/java/io/casehub/blocks/conversation/DefaultProgressRendererTest.java`

**Interfaces:**
- Consumes: `ProgressInstance` from `casehub-work-progress-api` (`shapeType()`, `state()`, `definition()`, `status()`, `scopeId()`)
- Produces: `ProgressRenderer.render(ProgressInstance) → String`, `DefaultProgressRenderer` (concrete implementation)

- [ ] **Step 1: Write failing tests for DefaultProgressRenderer**

Create `src/test/java/io/casehub/blocks/conversation/DefaultProgressRendererTest.java`:

```java
package io.casehub.blocks.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultProgressRendererTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final DefaultProgressRenderer renderer = new DefaultProgressRenderer();

    @Test
    void percentage_rendersValueWithLabel() {
        var pi = instance("percentage",
                defn().put("label", "Calibration"),
                state().put("value", 63),
                ProgressStatus.ACTIVE);

        assertThat(renderer.render(pi)).isEqualTo("Calibration: 63%");
    }

    @Test
    void percentage_completed_appendsCheckmark() {
        var pi = instance("percentage",
                defn().put("label", "Calibration"),
                state().put("value", 100),
                ProgressStatus.COMPLETED);

        assertThat(renderer.render(pi)).isEqualTo("Calibration: 100% ✓");
    }

    @Test
    void percentage_failed_appendsCross() {
        var pi = instance("percentage",
                defn().put("label", "Calibration"),
                state().put("value", 63),
                ProgressStatus.FAILED);

        assertThat(renderer.render(pi)).isEqualTo("Calibration: 63% ✗");
    }

    @Test
    void count_rendersCurrentOfTotal() {
        var def = defn().put("label", "Coverage").put("unit", "sensors");
        var pi = instance("count", def,
                state().put("current", 3).put("total", 7),
                ProgressStatus.ACTIVE);

        assertThat(renderer.render(pi)).isEqualTo("Coverage: 3 of 7 sensors");
    }

    @Test
    void count_withoutUnit_omitsUnit() {
        var pi = instance("count",
                defn().put("label", "Coverage"),
                state().put("current", 3).put("total", 7),
                ProgressStatus.ACTIVE);

        assertThat(renderer.render(pi)).isEqualTo("Coverage: 3 of 7");
    }

    @Test
    void count_completed_appendsCheckmark() {
        var pi = instance("count",
                defn().put("label", "Coverage"),
                state().put("current", 7).put("total", 7),
                ProgressStatus.COMPLETED);

        assertThat(renderer.render(pi)).isEqualTo("Coverage: 7 of 7 ✓");
    }

    @Test
    void count_failed_appendsCross() {
        var pi = instance("count",
                defn().put("label", "Coverage"),
                state().put("current", 3).put("total", 7),
                ProgressStatus.FAILED);

        assertThat(renderer.render(pi)).isEqualTo("Coverage: 3 of 7 ✗");
    }

    @Test
    void step_rendersArrowChain() {
        var def = MAPPER.createArrayNode()
                .add(MAPPER.createObjectNode().put("name", "unpack")
                        .putNull("condition").put("optional", false)
                        .putPOJO("dependsOn", java.util.List.of()))
                .add(MAPPER.createObjectNode().put("name", "assembly")
                        .putNull("condition").put("optional", false)
                        .putPOJO("dependsOn", java.util.List.of()))
                .add(MAPPER.createObjectNode().put("name", "calibration")
                        .putNull("condition").put("optional", false)
                        .putPOJO("dependsOn", java.util.List.of()))
                .add(MAPPER.createObjectNode().put("name", "testing")
                        .putNull("condition").put("optional", false)
                        .putPOJO("dependsOn", java.util.List.of()));

        var steps = MAPPER.createObjectNode();
        steps.set("unpack", MAPPER.createObjectNode().put("status", "COMPLETED"));
        steps.set("assembly", MAPPER.createObjectNode().put("status", "COMPLETED"));
        steps.set("calibration", MAPPER.createObjectNode().put("status", "ACTIVE"));
        steps.set("testing", MAPPER.createObjectNode().put("status", "PENDING"));

        var pi = instance("step", def, state().set("steps", steps), ProgressStatus.ACTIVE);

        assertThat(renderer.render(pi))
                .isEqualTo("unpack ✓ → assembly ✓ → calibration ⏳ → testing ○");
    }

    @Test
    void step_skippedAndFailed_renderCorrectGlyphs() {
        var def = MAPPER.createArrayNode()
                .add(MAPPER.createObjectNode().put("name", "a")
                        .putNull("condition").put("optional", false)
                        .putPOJO("dependsOn", java.util.List.of()))
                .add(MAPPER.createObjectNode().put("name", "b")
                        .putNull("condition").put("optional", true)
                        .putPOJO("dependsOn", java.util.List.of()))
                .add(MAPPER.createObjectNode().put("name", "c")
                        .putNull("condition").put("optional", false)
                        .putPOJO("dependsOn", java.util.List.of()));

        var steps = MAPPER.createObjectNode();
        steps.set("a", MAPPER.createObjectNode().put("status", "COMPLETED"));
        steps.set("b", MAPPER.createObjectNode().put("status", "SKIPPED"));
        steps.set("c", MAPPER.createObjectNode().put("status", "FAILED"));

        var pi = instance("step", def, state().set("steps", steps), ProgressStatus.FAILED);

        assertThat(renderer.render(pi)).isEqualTo("a ✓ → b ⊘ → c ✗");
    }

    @Test
    void unknownShape_fallsBackToStatusLabel() {
        var pi = instance("custom",
                defn().put("label", "Sync"),
                state(), ProgressStatus.ACTIVE);

        assertThat(renderer.render(pi)).isEqualTo("Sync: ACTIVE");
    }

    @Test
    void nullState_fallsBackToStatusLabel() {
        var pi = instance("percentage",
                defn().put("label", "Calibration"),
                null, ProgressStatus.ACTIVE);

        assertThat(renderer.render(pi)).isEqualTo("Calibration: ACTIVE");
    }

    @Test
    void nullDefinition_useScopeIdAsLabel() {
        var pi = new ProgressInstance(
                UUID.randomUUID(), "tenant-1", "WORK_ITEM", "cal-001",
                null, null, "percentage", null,
                state().put("value", 50),
                ProgressStatus.ACTIVE, null,
                Instant.now(), Instant.now());

        assertThat(renderer.render(pi)).isEqualTo("cal-001: 50%");
    }

    @Test
    void malformedState_fallsBackToStatusLabel() {
        var pi = instance("percentage",
                defn().put("label", "Calibration"),
                state().put("wrong", true),
                ProgressStatus.ACTIVE);

        assertThat(renderer.render(pi)).isEqualTo("Calibration: ACTIVE");
    }

    @Test
    void labelFromDefinition_takePrecedenceOverScopeId() {
        var pi = new ProgressInstance(
                UUID.randomUUID(), "tenant-1", "WORK_ITEM", "scope-xyz",
                null, null, "percentage",
                defn().put("label", "Calibration"),
                state().put("value", 42),
                ProgressStatus.ACTIVE, null,
                Instant.now(), Instant.now());

        assertThat(renderer.render(pi)).isEqualTo("Calibration: 42%");
    }

    private ProgressInstance instance(String shapeType, JsonNode definition,
            JsonNode state, ProgressStatus status) {
        return new ProgressInstance(
                UUID.randomUUID(), "tenant-1", "WORK_ITEM", "scope-1",
                null, null, shapeType, definition, state, status, null,
                Instant.now(), Instant.now());
    }

    private ObjectNode defn() {
        return MAPPER.createObjectNode();
    }

    private ObjectNode state() {
        return MAPPER.createObjectNode();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl . -Dtest=DefaultProgressRendererTest -DfailIfNoTests=false --batch-mode`
Expected: compilation failure — `ProgressRenderer` and `DefaultProgressRenderer` don't exist

- [ ] **Step 3: Create ProgressRenderer SPI**

Create `src/main/java/io/casehub/blocks/conversation/ProgressRenderer.java`:

```java
package io.casehub.blocks.conversation;

import io.casehub.work.progress.ProgressInstance;

@FunctionalInterface
public interface ProgressRenderer {
    String render(ProgressInstance progress);
}
```

- [ ] **Step 4: Create DefaultProgressRenderer**

Create `src/main/java/io/casehub/blocks/conversation/DefaultProgressRenderer.java`:

```java
package io.casehub.blocks.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressStatus;
import io.casehub.work.progress.StepDefinition;
import io.casehub.work.progress.StepStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DefaultProgressRenderer implements ProgressRenderer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<StepStatus, String> STEP_GLYPHS = Map.of(
            StepStatus.COMPLETED, "✓",
            StepStatus.ACTIVE, "⏳",
            StepStatus.SKIPPED, "⊘",
            StepStatus.FAILED, "✗",
            StepStatus.PENDING, "○"
    );

    @Override
    public String render(ProgressInstance progress) {
        String label = extractLabel(progress);
        if (progress.state() == null || progress.state().isMissingNode()) {
            return label + ": " + progress.status().name();
        }
        return switch (progress.shapeType()) {
            case "percentage" -> renderPercentage(label, progress);
            case "count" -> renderCount(label, progress);
            case "step" -> renderSteps(progress);
            default -> label + ": " + progress.status().name();
        };
    }

    private String renderPercentage(String label, ProgressInstance pi) {
        JsonNode valueNode = pi.state().get("value");
        if (valueNode == null || !valueNode.isInt()) {
            return label + ": " + pi.status().name();
        }
        String result = label + ": " + valueNode.intValue() + "%";
        return result + statusSuffix(pi.status());
    }

    private String renderCount(String label, ProgressInstance pi) {
        JsonNode currentNode = pi.state().get("current");
        JsonNode totalNode = pi.state().get("total");
        if (currentNode == null || !currentNode.isInt()
                || totalNode == null || !totalNode.isInt()) {
            return label + ": " + pi.status().name();
        }
        var sb = new StringBuilder();
        sb.append(label).append(": ")
          .append(currentNode.intValue()).append(" of ").append(totalNode.intValue());
        if (pi.definition() != null && pi.definition().has("unit")) {
            sb.append(" ").append(pi.definition().get("unit").asText());
        }
        sb.append(statusSuffix(pi.status()));
        return sb.toString();
    }

    private String renderSteps(ProgressInstance pi) {
        if (pi.definition() == null || pi.definition().isMissingNode()) {
            return extractLabel(pi) + ": " + pi.status().name();
        }
        List<StepDefinition> stepDefs;
        try {
            stepDefs = MAPPER.readerForListOf(StepDefinition.class)
                    .readValue(pi.definition());
        } catch (Exception e) {
            return extractLabel(pi) + ": " + pi.status().name();
        }
        JsonNode stepsState = pi.state().get("steps");
        if (stepsState == null || !stepsState.isObject()) {
            return extractLabel(pi) + ": " + pi.status().name();
        }
        var parts = new ArrayList<String>();
        for (StepDefinition def : stepDefs) {
            StepStatus status = StepStatus.PENDING;
            JsonNode stepNode = stepsState.get(def.name());
            if (stepNode != null && stepNode.has("status")) {
                try {
                    status = StepStatus.valueOf(
                            stepNode.get("status").asText().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {}
            }
            parts.add(def.name() + " " + STEP_GLYPHS.getOrDefault(status, "?"));
        }
        return String.join(" → ", parts);
    }

    private String extractLabel(ProgressInstance pi) {
        if (pi.definition() != null && pi.definition().has("label")) {
            return pi.definition().get("label").asText();
        }
        return pi.scopeId();
    }

    private String statusSuffix(ProgressStatus status) {
        return switch (status) {
            case COMPLETED -> " ✓";
            case FAILED -> " ✗";
            default -> "";
        };
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -pl . -Dtest=DefaultProgressRendererTest --batch-mode`
Expected: all 13 tests PASS

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add src/main/java/io/casehub/blocks/conversation/ProgressRenderer.java src/main/java/io/casehub/blocks/conversation/DefaultProgressRenderer.java src/test/java/io/casehub/blocks/conversation/DefaultProgressRendererTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#62): add ProgressRenderer SPI and DefaultProgressRenderer"
```

---

### Task 3: ConversationRendererConfig and ConversationRenderer integration

**Files:**
- Modify: `src/main/java/io/casehub/blocks/conversation/ConversationRendererConfig.java`
- Modify: `src/main/java/io/casehub/blocks/conversation/ConversationRenderer.java`
- Modify: `src/test/java/io/casehub/blocks/conversation/ConversationRendererTest.java`

**Interfaces:**
- Consumes: `ProgressRenderer` (from Task 2), `RenderContext.progress()` (from Task 1)
- Produces: `ConversationRendererConfig.showProgress()`, `ConversationRenderer(config, progressRenderer)` constructor

- [ ] **Step 1: Write failing tests for progress rendering**

Add to the existing `ConversationRendererTest.java`:

```java
@Test
void progress_inTopicView_rendersBetweenHeaderAndObligationChain() {
    var config = ConversationRendererConfig.builder()
            .groupByTopic(true).showProgress(true).build();
    var renderer = new ConversationRenderer(config);

    var point = point("p1", "review", "open", Priority.HIGH, "scope",
            "Review the code");
    var pi = testProgressInstance("percentage",
            mapper().createObjectNode().put("label", "Code quality"),
            mapper().createObjectNode().put("value", 63));
    var ctx = new RenderContext(Map.of(), null, null,
            Map.of("review", List.of(pi)));

    var result = renderer.render(state(List.of(point)), ctx);

    assertThat(result).contains("## review\n\nCode quality: 63%\n");
}

@Test
void progress_inFlatView_singleTopic_rendersUnderProgressHeader() {
    var config = ConversationRendererConfig.builder()
            .showProgress(true).build();
    var renderer = new ConversationRenderer(config);

    var point = point("p1", "review", "open", Priority.HIGH, "scope",
            "Review the code");
    var pi = testProgressInstance("count",
            mapper().createObjectNode().put("label", "Files"),
            mapper().createObjectNode().put("current", 3).put("total", 10));
    var ctx = new RenderContext(Map.of(), null, null,
            Map.of("review", List.of(pi)));

    var result = renderer.render(state(List.of(point)), ctx);

    assertThat(result).contains("**Progress**\n");
    assertThat(result).contains("Files: 3 of 10\n");
    assertThat(result).doesNotContain("_review:_");
}

@Test
void progress_inFlatView_multiTopic_prefixesWithTopicName() {
    var config = ConversationRendererConfig.builder()
            .showProgress(true).build();
    var renderer = new ConversationRenderer(config);

    var point = point("p1", "general", "open", Priority.HIGH, "scope",
            "A point");
    var pi1 = testProgressInstance("percentage",
            mapper().createObjectNode().put("label", "Quality"),
            mapper().createObjectNode().put("value", 50));
    var pi2 = testProgressInstance("percentage",
            mapper().createObjectNode().put("label", "Coverage"),
            mapper().createObjectNode().put("value", 80));
    var progress = new java.util.LinkedHashMap<String, List<ProgressInstance>>();
    progress.put("review", List.of(pi1));
    progress.put("analysis", List.of(pi2));
    var ctx = new RenderContext(Map.of(), null, null, progress);

    var result = renderer.render(state(List.of(point)), ctx);

    assertThat(result).contains("_review:_");
    assertThat(result).contains("_analysis:_");
}

@Test
void progress_showProgressFalse_noProgressInOutput() {
    var config = ConversationRendererConfig.builder()
            .showProgress(false).build();
    var renderer = new ConversationRenderer(config);

    var point = point("p1", "review", "open", Priority.HIGH, "scope",
            "A point");
    var pi = testProgressInstance("percentage",
            mapper().createObjectNode().put("label", "Quality"),
            mapper().createObjectNode().put("value", 63));
    var ctx = new RenderContext(Map.of(), null, null,
            Map.of("review", List.of(pi)));

    var result = renderer.render(state(List.of(point)), ctx);

    assertThat(result).doesNotContain("Quality");
    assertThat(result).doesNotContain("63%");
}

@Test
void progress_emptyMap_noCrashNoOutput() {
    var config = ConversationRendererConfig.builder()
            .showProgress(true).build();
    var renderer = new ConversationRenderer(config);

    var point = point("p1", "review", "open", Priority.HIGH, "scope",
            "A point");
    var ctx = new RenderContext(Map.of(), null, null, Map.of());

    var result = renderer.render(state(List.of(point)), ctx);

    assertThat(result).doesNotContain("Progress");
}

@Test
void progress_customRenderer_isUsed() {
    var config = ConversationRendererConfig.builder()
            .showProgress(true).groupByTopic(true).build();
    ProgressRenderer custom = pi -> "CUSTOM:" + pi.shapeType();
    var renderer = new ConversationRenderer(config, custom);

    var point = point("p1", "review", "open", Priority.HIGH, "scope",
            "A point");
    var pi = testProgressInstance("percentage",
            mapper().createObjectNode().put("label", "Q"),
            mapper().createObjectNode().put("value", 50));
    var ctx = new RenderContext(Map.of(), null, null,
            Map.of("review", List.of(pi)));

    var result = renderer.render(state(List.of(point)), ctx);

    assertThat(result).contains("CUSTOM:percentage");
}

@Test
void progress_topicWithNoProgress_rendersNormally() {
    var config = ConversationRendererConfig.builder()
            .groupByTopic(true).showProgress(true).build();
    var renderer = new ConversationRenderer(config);

    var point = point("p1", "review", "open", Priority.HIGH, "scope",
            "A point");
    var ctx = new RenderContext(Map.of(), null, null, Map.of());

    var result = renderer.render(state(List.of(point)), ctx);

    assertThat(result).contains("## review\n\n##");
}

@Test
void progress_orderingPreserved() {
    var config = ConversationRendererConfig.builder()
            .groupByTopic(true).showProgress(true).build();
    var renderer = new ConversationRenderer(config);

    var point = point("p1", "review", "open", Priority.HIGH, "scope",
            "A point");
    var pi1 = testProgressInstance("percentage",
            mapper().createObjectNode().put("label", "First"),
            mapper().createObjectNode().put("value", 10));
    var pi2 = testProgressInstance("percentage",
            mapper().createObjectNode().put("label", "Second"),
            mapper().createObjectNode().put("value", 20));
    var ctx = new RenderContext(Map.of(), null, null,
            Map.of("review", List.of(pi1, pi2)));

    var result = renderer.render(state(List.of(point)), ctx);

    int firstIdx = result.indexOf("First: 10%");
    int secondIdx = result.indexOf("Second: 20%");
    assertThat(firstIdx).isLessThan(secondIdx);
}
```

Also add these helper methods to the test class:

```java
private static ProgressInstance testProgressInstance(String shapeType,
        com.fasterxml.jackson.databind.JsonNode definition,
        com.fasterxml.jackson.databind.JsonNode state) {
    return new ProgressInstance(
            UUID.randomUUID(), "tenant-1", "WORK_ITEM", "scope-1",
            null, null, shapeType, definition, state,
            ProgressStatus.ACTIVE, null,
            Instant.now(), Instant.now());
}

private static com.fasterxml.jackson.databind.ObjectMapper mapper() {
    return new com.fasterxml.jackson.databind.ObjectMapper();
}
```

And these imports at the top:

```java
import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressStatus;
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl . -Dtest=ConversationRendererTest -DfailIfNoTests=false --batch-mode`
Expected: compilation failure — `showProgress()` doesn't exist on config

- [ ] **Step 3: Add showProgress to ConversationRendererConfig**

Add the field to the record (after `showConvergenceSignal`):

```java
boolean showProgress
```

Add to the compact constructor defaults. Add `showProgress` to the Builder:
- Field: `private boolean showProgress;`
- Setter: `public Builder showProgress(boolean showProgress) { this.showProgress = showProgress; return this; }`
- Include in `build()` call
- Include in `toBuilder()` copy

- [ ] **Step 4: Add ProgressRenderer to ConversationRenderer**

Add the `progressRenderer` field and two-arg constructor:

```java
private final ProgressRenderer progressRenderer;

public ConversationRenderer(ConversationRendererConfig config) {
    this(config, new DefaultProgressRenderer());
}

public ConversationRenderer(ConversationRendererConfig config,
                            ProgressRenderer progressRenderer) {
    this.config = config;
    this.progressRenderer = progressRenderer;
}
```

Add progress rendering to `renderByTopic()` — after the topic header
line `sb.append("## ").append(entry.getKey()).append("\n\n");`, before
the obligation chain block:

```java
if (config.showProgress() && ctx.progress().containsKey(entry.getKey())) {
    for (ProgressInstance pi : ctx.progress().get(entry.getKey())) {
        sb.append(progressRenderer.render(pi)).append("\n");
    }
    sb.append("\n");
}
```

Add progress rendering to `renderFlat()` — at the top before status
grouping:

```java
if (config.showProgress() && !ctx.progress().isEmpty()) {
    sb.append("**Progress**\n");
    for (var topicEntry : ctx.progress().entrySet()) {
        if (ctx.progress().size() > 1) {
            sb.append("_").append(topicEntry.getKey()).append(":_ ");
        }
        for (ProgressInstance pi : topicEntry.getValue()) {
            sb.append(progressRenderer.render(pi)).append("\n");
        }
    }
    sb.append("\n");
}
```

Add import: `import io.casehub.work.progress.ProgressInstance;`

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test --batch-mode`
Expected: all tests pass including the 8 new progress tests

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add src/main/java/io/casehub/blocks/conversation/ConversationRendererConfig.java src/main/java/io/casehub/blocks/conversation/ConversationRenderer.java src/test/java/io/casehub/blocks/conversation/ConversationRendererTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#62): integrate progress rendering into ConversationRenderer"
```

---

### Task 4: Documentation sync

**Files:**
- Modify: `CLAUDE.md` — update `RenderContext` description and `ConversationRendererConfig` fields
- Modify: `docs/guides/consumer-guide.md` — update `RenderContext` row

**Interfaces:**
- Consumes: none (documentation only)
- Produces: updated documentation reflecting new progress field

- [ ] **Step 1: Update CLAUDE.md**

In the `RenderContext` row of the conversation package table, change:
```
| `RenderContext` | Supplementary render-time inputs: reactions, commonGround, convergence. Replaces renderer overloads. |
```
to:
```
| `RenderContext` | Supplementary render-time inputs: reactions, commonGround, convergence, progress. Replaces renderer overloads. |
```

Add new rows to the conversation package table:
```
| `ProgressRenderer` | `@FunctionalInterface` SPI: `String render(ProgressInstance)`. Pluggable progress-to-text rendering for LLM agent prompts. |
| `DefaultProgressRenderer` | Built-in `ProgressRenderer`: percentage (`"Label: N%"`), count (`"Label: N of M"`), step (`"a ✓ → b ⏳ → c ○"`), fallback to status name. Null-safe. |
```

- [ ] **Step 2: Update consumer-guide.md**

In the `RenderContext` row, update the description to include `progress (Map)`.

- [ ] **Step 3: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add CLAUDE.md docs/guides/consumer-guide.md
git -C /Users/mdproctor/claude/casehub/blocks commit -m "docs(#62): update CLAUDE.md and consumer guide with progress rendering types"
```
