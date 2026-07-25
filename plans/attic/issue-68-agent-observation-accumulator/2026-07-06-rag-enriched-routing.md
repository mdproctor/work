# RAG-Enriched Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #16 — feat: RAG-enriched supervisor routing
**Issue group:** #16

**Goal:** Build a composable prompt enrichment pipeline for the LLM routing strategy,
backed by CBR case history retrieval and outcome recording, creating a feedback loop
where routing decisions improve over time.

**Architecture:** Three new SPIs (RoutingPromptSection, RoutingFeatureExtractor,
RoutingOutcomeRecorder) with CDI-discovered implementations. The LLM strategy
delegates prompt assembly to a RoutingPromptAssembler that renders all available
sections. CBR history feeds the LLM prompt; routing outcomes feed back into the
CBR store. Engine-api gains the RoutingOutcomeRecorder SPI; engine runtime calls
it at worker completion.

**Tech Stack:** Java 21, Quarkus CDI, Mutiny, JUnit 5, Mockito, AssertJ

## Global Constraints

- blocks has no Quarkus runtime — plain JUnit 5 with Mockito, no CDI container in tests
- engine-api changes must not add new dependencies
- All optional dependencies use `Instance<T>` with `isUnsatisfied()` checks
- `@DefaultBean` pattern for single-bean replacement SPIs (RoutingFeatureExtractor)
- Config properties: `casehub.blocks.cbr.top-k` (default 10), `casehub.blocks.cbr.min-similarity` (default 0.5)
- CBR outcome vocabulary: `"SUCCESS"` / `"FAILURE"` — matches `CbrAgentRoutingStrategy.analyseByType()`
- Cross-repo commits on main approved for engine-api and engine runtime

## Dependency Graph

```
Task 1: RoutingFeatureExtractor ─────┬──→ Task 3: CbrRoutingPromptSection
Task 2: RoutingPromptSection/Assembler ┘        │
                                                 ├──→ Task 4: LlmAgentRoutingStrategy refactor
                                                 │
Task 1 ──→ Task 5: CbrAgentRoutingStrategy adoption
Task 6: RoutingOutcomeRecorder SPI ──→ Task 7: CbrRoutingOutcomeRecorder
                                    └──→ Task 8: Engine runtime integration
```

Tasks 1, 2, and 6 are independent foundations — can execute in parallel.

---

### Task 1: RoutingFeatureExtractor SPI + TextOnlyFeatureExtractor

**Files:**
- Create: `src/main/java/io/casehub/blocks/routing/agent/RoutingFeatureExtractor.java`
- Create: `src/main/java/io/casehub/blocks/routing/agent/TextOnlyFeatureExtractor.java`
- Test: `src/test/java/io/casehub/blocks/routing/agent/TextOnlyFeatureExtractorTest.java`

**Interfaces:**
- Consumes: `AgentRoutingContext` (engine-api, already a dependency)
- Produces: `RoutingFeatureExtractor` interface with `extractFeatures(AgentRoutingContext)` → `Map<String, Object>` and `extractProblem(AgentRoutingContext)` → `@Nullable String`. Used by Tasks 3, 5, 7.

- [ ] **Step 1: Write the failing tests**

```java
package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.casehub.api.spi.routing.AgentRoutingContext;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TextOnlyFeatureExtractorTest {

    private final TextOnlyFeatureExtractor extractor = new TextOnlyFeatureExtractor();

    @Test
    void extractFeatures_returnsEmptyMap() {
        var ctx = new AgentRoutingContext(UUID.randomUUID(), "cap", NullNode.instance, "t");
        assertThat(extractor.extractFeatures(ctx)).isEmpty();
    }

    @Test
    void extractProblem_withObjectNode_returnsStringRepresentation() {
        var node = JsonNodeFactory.instance.objectNode().put("key", "value");
        var ctx = new AgentRoutingContext(UUID.randomUUID(), "cap", node, "t");
        assertThat(extractor.extractProblem(ctx)).isNotNull().contains("key");
    }

    @Test
    void extractProblem_withNullNode_returnsNull() {
        var ctx = new AgentRoutingContext(UUID.randomUUID(), "cap", NullNode.instance, "t");
        assertThat(extractor.extractProblem(ctx)).isNull();
    }

    @Test
    void extractProblem_withNullContext_returnsNull() {
        var ctx = new AgentRoutingContext(UUID.randomUUID(), "cap", null, "t");
        assertThat(extractor.extractProblem(ctx)).isNull();
    }

    @Test
    void extractProblem_withBlankTextNode_returnsNull() {
        var ctx = new AgentRoutingContext(UUID.randomUUID(), "cap", new TextNode("   "), "t");
        assertThat(extractor.extractProblem(ctx)).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl . -Dtest=TextOnlyFeatureExtractorTest -DfailIfNoTests=false`
Expected: compilation failure — classes do not exist yet

- [ ] **Step 3: Write the interface and implementation**

`RoutingFeatureExtractor.java`:
```java
package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentRoutingContext;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public interface RoutingFeatureExtractor {
    Map<String, Object> extractFeatures(AgentRoutingContext context);
    @Nullable String extractProblem(AgentRoutingContext context);
}
```

`TextOnlyFeatureExtractor.java`:
```java
package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentRoutingContext;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@ApplicationScoped
@DefaultBean
public class TextOnlyFeatureExtractor implements RoutingFeatureExtractor {

    @Override
    public Map<String, Object> extractFeatures(AgentRoutingContext context) {
        return Map.of();
    }

    @Override
    public @Nullable String extractProblem(AgentRoutingContext context) {
        if (context.caseContext() == null || context.caseContext().isNull()) {
            return null;
        }
        String text = context.caseContext().toString();
        return text.isBlank() ? null : text;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl . -Dtest=TextOnlyFeatureExtractorTest`
Expected: all 5 tests PASS

- [ ] **Step 5: Commit**

```
feat(#16): RoutingFeatureExtractor SPI + TextOnlyFeatureExtractor default
```

---

### Task 2: RoutingPromptSection SPI + RoutingPromptAssembler

**Files:**
- Create: `src/main/java/io/casehub/blocks/routing/agent/RoutingPromptSection.java`
- Create: `src/main/java/io/casehub/blocks/routing/agent/RoutingPromptAssembler.java`
- Test: `src/test/java/io/casehub/blocks/routing/agent/RoutingPromptAssemblerTest.java`

**Interfaces:**
- Consumes: `AgentRoutingContext`, `AgentCandidate` (engine-api)
- Produces: `RoutingPromptSection` interface with `render(AgentRoutingContext, List<AgentCandidate>)` → `@Nullable String`. `RoutingPromptAssembler` with `assemble(AgentRoutingContext, List<AgentCandidate>)` → `@Nullable String`. Used by Task 4.

- [ ] **Step 1: Write the failing tests**

```java
package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.eidos.api.MatchDegree;
import jakarta.annotation.Priority;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingPromptAssemblerTest {

    private AgentRoutingContext context() {
        return new AgentRoutingContext(UUID.randomUUID(), "analysis", NullNode.instance, "t");
    }

    private List<AgentCandidate> candidates() {
        return List.of(new AgentCandidate("a", Set.of("analysis"), 0,
                AgentHealth.READY, null, new MatchDegree.None()));
    }

    @Test
    void noSections_returnsNull() {
        var assembler = new RoutingPromptAssembler(List.of());
        assertThat(assembler.assemble(context(), candidates())).isNull();
    }

    @Test
    void singleSection_returnsRenderedContent() {
        RoutingPromptSection section = (ctx, eligible) -> "Historical context: 3 cases";
        var assembler = new RoutingPromptAssembler(List.of(section));
        assertThat(assembler.assemble(context(), candidates()))
                .isEqualTo("Historical context: 3 cases");
    }

    @Test
    void sectionReturnsNull_skipped() {
        RoutingPromptSection nullSection = (ctx, eligible) -> null;
        RoutingPromptSection realSection = (ctx, eligible) -> "data";
        var assembler = new RoutingPromptAssembler(List.of(nullSection, realSection));
        assertThat(assembler.assemble(context(), candidates())).isEqualTo("data");
    }

    @Test
    void sectionReturnsBlank_skipped() {
        RoutingPromptSection blankSection = (ctx, eligible) -> "   ";
        RoutingPromptSection realSection = (ctx, eligible) -> "data";
        var assembler = new RoutingPromptAssembler(List.of(blankSection, realSection));
        assertThat(assembler.assemble(context(), candidates())).isEqualTo("data");
    }

    @Test
    void multipleSections_joinedWithDoubleNewline() {
        RoutingPromptSection s1 = (ctx, eligible) -> "section-1";
        RoutingPromptSection s2 = (ctx, eligible) -> "section-2";
        var assembler = new RoutingPromptAssembler(List.of(s1, s2));
        assertThat(assembler.assemble(context(), candidates()))
                .isEqualTo("section-1\n\nsection-2");
    }

    @Test
    void throwingSection_loggedAndSkipped_otherSectionsSurvive() {
        RoutingPromptSection boom = (ctx, eligible) -> { throw new RuntimeException("fail"); };
        RoutingPromptSection ok = (ctx, eligible) -> "survived";
        var assembler = new RoutingPromptAssembler(List.of(boom, ok));
        assertThat(assembler.assemble(context(), candidates())).isEqualTo("survived");
    }

    @Test
    void allSectionsReturnNull_returnsNull() {
        RoutingPromptSection n1 = (ctx, eligible) -> null;
        RoutingPromptSection n2 = (ctx, eligible) -> null;
        var assembler = new RoutingPromptAssembler(List.of(n1, n2));
        assertThat(assembler.assemble(context(), candidates())).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl . -Dtest=RoutingPromptAssemblerTest -DfailIfNoTests=false`
Expected: compilation failure

- [ ] **Step 3: Write the interface and assembler**

`RoutingPromptSection.java`:
```java
package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface RoutingPromptSection {
    @Nullable String render(AgentRoutingContext context, List<AgentCandidate> eligible);
}
```

`RoutingPromptAssembler.java`:
```java
package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class RoutingPromptAssembler {

    private static final System.Logger LOG =
            System.getLogger(RoutingPromptAssembler.class.getName());

    private final List<RoutingPromptSection> sections;

    @Inject
    public RoutingPromptAssembler(Instance<RoutingPromptSection> sections) {
        this.sections = sections.stream()
                .sorted(Comparator.comparingInt(RoutingPromptAssembler::priority))
                .toList();
    }

    public RoutingPromptAssembler(List<RoutingPromptSection> sections) {
        this.sections = List.copyOf(sections);
    }

    public @Nullable String assemble(AgentRoutingContext context,
                                      List<AgentCandidate> eligible) {
        var sb = new StringBuilder();
        for (var section : sections) {
            try {
                String rendered = section.render(context, eligible);
                if (rendered != null && !rendered.isBlank()) {
                    if (!sb.isEmpty()) sb.append("\n\n");
                    sb.append(rendered);
                }
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING,
                        "RoutingPromptSection threw — skipping: "
                                + section.getClass().getName(), e);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static int priority(RoutingPromptSection section) {
        var annotation = section.getClass().getAnnotation(jakarta.annotation.Priority.class);
        return annotation != null ? annotation.value() : Integer.MAX_VALUE;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl . -Dtest=RoutingPromptAssemblerTest`
Expected: all 7 tests PASS

- [ ] **Step 5: Commit**

```
feat(#16): RoutingPromptSection SPI + RoutingPromptAssembler
```

---

### Task 3: CbrRoutingPromptSection

**Files:**
- Create: `src/main/java/io/casehub/blocks/routing/agent/CbrRoutingPromptSection.java`
- Test: `src/test/java/io/casehub/blocks/routing/agent/CbrRoutingPromptSectionTest.java`

**Interfaces:**
- Consumes: `RoutingPromptSection` (Task 2), `RoutingFeatureExtractor` (Task 1), `CbrCaseMemoryStore` (neural-text-memory-api), `AgentRoutingContext`, `AgentCandidate`
- Produces: `CbrRoutingPromptSection` — CDI bean implementing `RoutingPromptSection`. Renders historical context from CBR into a prompt section string.

- [ ] **Step 1: Write the failing tests**

```java
package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.eidos.api.MatchDegree;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.memory.cbr.TextualCbrCase;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CbrRoutingPromptSectionTest {

    @Mock CbrCaseMemoryStore cbrStore;

    private final RoutingFeatureExtractor extractor = new TextOnlyFeatureExtractor();

    private CbrRoutingPromptSection section;

    private AgentRoutingContext context() {
        return new AgentRoutingContext(UUID.randomUUID(), "analysis", NullNode.instance, "t");
    }

    private AgentCandidate candidate(String id) {
        return new AgentCandidate(id, Set.of("analysis"), 0,
                AgentHealth.READY, null, new MatchDegree.None());
    }

    @BeforeEach
    void setUp() {
        section = new CbrRoutingPromptSection(cbrStore, extractor, 10, 0.5);
    }

    @Test
    void returnsNull_whenCbrStoreNull() {
        var noStore = new CbrRoutingPromptSection(null, extractor, 10, 0.5);
        assertThat(noStore.render(context(), List.of(candidate("a")))).isNull();
    }

    @Test
    void returnsNull_whenNoSimilarCases() {
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of());
        assertThat(section.render(context(), List.of(candidate("a")))).isNull();
    }

    @Test
    void rendersPlanCbrCase_withEligibleAgent() {
        var trace = new PlanTrace("binding-a", "analysis", "agent-a", "SUCCESS", 1, Map.of());
        var cbrCase = new PlanCbrCase("problem", "solution", "RESOLVED", 0.9,
                Map.of(), List.of(trace));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

        var result = section.render(context(), List.of(candidate("agent-a")));

        assertThat(result)
                .contains("Historical context")
                .contains("agent-a")
                .contains("SUCCESS");
    }

    @Test
    void filtersOutNonEligibleAgents() {
        var traceA = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
        var traceB = new PlanTrace("b", "analysis", "excluded", "FAILURE", 1, Map.of());
        var cbrCase = new PlanCbrCase("problem", "sol", "RESOLVED", 0.9,
                Map.of(), List.of(traceA, traceB));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

        var result = section.render(context(), List.of(candidate("agent-a")));

        assertThat(result).contains("agent-a").doesNotContain("excluded");
    }

    @Test
    void returnsNull_whenAllCasesReferenceNonEligibleAgents() {
        var trace = new PlanTrace("b", "analysis", "excluded", "SUCCESS", 1, Map.of());
        var cbrCase = new PlanCbrCase("problem", "sol", "RESOLVED", 0.9,
                Map.of(), List.of(trace));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

        assertThat(section.render(context(), List.of(candidate("other")))).isNull();
    }

    @Test
    void rendersTextualCbrCase_withoutAgentAttribution() {
        var textCase = new TextualCbrCase("similar problem", "some solution", "RESOLVED", 0.8);
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(textCase, 0.85)));

        var result = section.render(context(), List.of(candidate("a")));

        assertThat(result)
                .contains("similar problem")
                .contains("RESOLVED")
                .doesNotContain("Outcomes by agent");
    }

    @Test
    void mixedCases_planAndTextual() {
        var trace = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
        var planCase = new PlanCbrCase("plan problem", "sol", "RESOLVED", 0.9,
                Map.of(), List.of(trace));
        var textCase = new TextualCbrCase("text problem", "sol", "FAILURE", 0.7);
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(
                        new ScoredCbrCase<>(planCase, 0.9),
                        new ScoredCbrCase<>(textCase, 0.85)));

        var result = section.render(context(), List.of(candidate("agent-a")));

        assertThat(result)
                .contains("Outcomes by agent")
                .contains("agent-a")
                .contains("text problem");
    }

    @Test
    void cbrStoreThrows_returnsNull() {
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenThrow(new RuntimeException("store error"));
        assertThat(section.render(context(), List.of(candidate("a")))).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl . -Dtest=CbrRoutingPromptSectionTest -DfailIfNoTests=false`
Expected: compilation failure

- [ ] **Step 3: Write the implementation**

`CbrRoutingPromptSection.java`:
```java
package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class CbrRoutingPromptSection implements RoutingPromptSection {

    private static final System.Logger LOG =
            System.getLogger(CbrRoutingPromptSection.class.getName());

    private final @Nullable CbrCaseMemoryStore cbrStore;
    private final RoutingFeatureExtractor featureExtractor;
    private final int topK;
    private final double minSimilarity;

    @Inject
    public CbrRoutingPromptSection(
            Instance<CbrCaseMemoryStore> cbrStore,
            Instance<RoutingFeatureExtractor> featureExtractor,
            @ConfigProperty(name = "casehub.blocks.cbr.top-k",
                            defaultValue = "10") int topK,
            @ConfigProperty(name = "casehub.blocks.cbr.min-similarity",
                            defaultValue = "0.5") double minSimilarity) {
        this.cbrStore = cbrStore.isUnsatisfied() ? null : cbrStore.get();
        this.featureExtractor = featureExtractor.isUnsatisfied()
                ? new TextOnlyFeatureExtractor() : featureExtractor.get();
        this.topK = topK;
        this.minSimilarity = minSimilarity;
    }

    CbrRoutingPromptSection(@Nullable CbrCaseMemoryStore cbrStore,
                            RoutingFeatureExtractor featureExtractor,
                            int topK, double minSimilarity) {
        this.cbrStore = cbrStore;
        this.featureExtractor = featureExtractor;
        this.topK = topK;
        this.minSimilarity = minSimilarity;
    }

    @Override
    public @Nullable String render(AgentRoutingContext context,
                                    List<AgentCandidate> eligible) {
        if (cbrStore == null) return null;

        try {
            var query = CbrQuery.of(
                    context.tenancyId(),
                    new MemoryDomain(context.capabilityName()),
                    "agent-routing",
                    featureExtractor.extractFeatures(context),
                    topK)
                    .withMinSimilarity(minSimilarity);

            String problem = featureExtractor.extractProblem(context);
            if (problem != null) {
                query = query.withProblem(problem);
            }

            List<ScoredCbrCase<CbrCase>> results =
                    cbrStore.retrieveSimilar(query, CbrCase.class);
            if (results.isEmpty()) return null;

            Set<String> eligibleIds = eligible.stream()
                    .map(AgentCandidate::workerId)
                    .collect(Collectors.toSet());

            return format(results, eligibleIds, context.capabilityName());
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "CBR query failed", e);
            return null;
        }
    }

    private @Nullable String format(List<ScoredCbrCase<CbrCase>> results,
                                     Set<String> eligibleIds,
                                     String capabilityName) {
        // Tally agent outcomes from PlanCbrCase entries
        Map<String, int[]> agentStats = new HashMap<>(); // [successes, total]
        var caseDetails = new StringBuilder();
        int caseNum = 0;

        for (var scored : results) {
            CbrCase cbrCase = scored.cbrCase();
            if (cbrCase instanceof PlanCbrCase planCase) {
                boolean hasEligibleTrace = false;
                for (PlanTrace trace : planCase.planTrace()) {
                    if (capabilityName.equals(trace.capabilityName())
                            && trace.workerName() != null
                            && eligibleIds.contains(trace.workerName())) {
                        hasEligibleTrace = true;
                        var stats = agentStats.computeIfAbsent(
                                trace.workerName(), k -> new int[]{0, 0});
                        stats[1]++;
                        if ("SUCCESS".equals(trace.stepOutcome())) {
                            stats[0]++;
                        }
                        caseNum++;
                        caseDetails.append("  %d. [score: %.2f] problem: \"%s\" → agent: \"%s\" → %s%n"
                                .formatted(caseNum, scored.score(),
                                        truncate(planCase.problem(), 60),
                                        trace.workerName(), trace.stepOutcome()));
                    }
                }
                if (!hasEligibleTrace && planCase.problem() != null) {
                    // Plan case with no eligible agent traces — skip from details
                }
            } else {
                // TextualCbrCase or unknown — show without agent attribution
                caseNum++;
                caseDetails.append("  %d. [score: %.2f] problem: \"%s\" → %s%n"
                        .formatted(caseNum, scored.score(),
                                truncate(cbrCase.problem(), 60),
                                cbrCase.outcome() != null ? cbrCase.outcome() : "UNKNOWN"));
            }
        }

        if (caseNum == 0) return null;

        var sb = new StringBuilder();
        sb.append("Historical context (%d similar past cases for capability \"%s\"):\n"
                .formatted(caseNum, capabilityName));

        if (!agentStats.isEmpty()) {
            sb.append("\nOutcomes by agent:\n");
            agentStats.forEach((agent, stats) -> {
                int successes = stats[0];
                int total = stats[1];
                int failures = total - successes;
                int pct = total > 0 ? (int) Math.round(100.0 * successes / total) : 0;
                sb.append("  \"%s\": %d cases — %d SUCCESS, %d FAILURE (%d%% success)%n"
                        .formatted(agent, total, successes, failures, pct));
            });
        }

        sb.append("\nCase details:\n");
        sb.append(caseDetails);
        return sb.toString().stripTrailing();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl . -Dtest=CbrRoutingPromptSectionTest`
Expected: all 8 tests PASS

- [ ] **Step 5: Commit**

```
feat(#16): CbrRoutingPromptSection — CBR history prompt enrichment
```

---

### Task 4: LlmAgentRoutingStrategy refactor + RoutingSupport system prompt

**Files:**
- Modify: `src/main/java/io/casehub/blocks/routing/agent/LlmAgentRoutingStrategy.java`
- Modify: `src/main/java/io/casehub/blocks/routing/agent/RoutingSupport.java`
- Modify: `src/test/java/io/casehub/blocks/routing/agent/LlmAgentRoutingStrategyTest.java`

**Interfaces:**
- Consumes: `RoutingPromptAssembler` (Task 2)
- Produces: Updated `LlmAgentRoutingStrategy` that discovers and appends prompt sections

- [ ] **Step 1: Write new tests for enrichment behaviour**

Add to `LlmAgentRoutingStrategyTest.java` a new `@Nested` class:

```java
@Nested
class WithPromptEnrichment {
    @Test
    void assemblerOutputAppendedToPrompt() {
        var assembler = mock(RoutingPromptAssembler.class);
        when(assembler.assemble(any(), any())).thenReturn("Historical context: 3 cases");
        agentReturns("{\"agent\": \"agent-a\", \"reason\": \"best\"}");

        var strategy = new LlmAgentRoutingStrategy(
                presentInstance(agentProvider), absentInstance(),
                absentInstance(), absentInstance(), assembler);

        var result = strategy.select(context("analysis"),
                List.of(candidate("agent-a"))).await().indefinitely();

        assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
        // Verify the prompt passed to agentProvider includes the enrichment
        var configCaptor = org.mockito.ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(configCaptor.capture());
        assertThat(configCaptor.getValue().userPrompt())
                .contains("Historical context: 3 cases");
    }

    @Test
    void assemblerReturnsNull_promptUnchanged() {
        var assembler = mock(RoutingPromptAssembler.class);
        when(assembler.assemble(any(), any())).thenReturn(null);
        agentReturns("{\"agent\": \"agent-a\", \"reason\": \"best\"}");

        var strategy = new LlmAgentRoutingStrategy(
                presentInstance(agentProvider), absentInstance(),
                absentInstance(), absentInstance(), assembler);

        var result = strategy.select(context("analysis"),
                List.of(candidate("agent-a"))).await().indefinitely();

        assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
        var configCaptor = org.mockito.ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(configCaptor.capture());
        assertThat(configCaptor.getValue().userPrompt())
                .doesNotContain("Historical context");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl . -Dtest=LlmAgentRoutingStrategyTest`
Expected: compilation failure — constructor signature doesn't match

- [ ] **Step 3: Update LlmAgentRoutingStrategy**

Add `RoutingPromptAssembler` to the constructor. In `doSelect()`, after building
the prompt, call `promptAssembler.assemble()` and append if non-null.

Update `RoutingSupport.SYSTEM_PROMPT` to include historical evidence guidance:
```java
static final String SYSTEM_PROMPT = """
        You are an agent router. Given a capability name, case context, and a list of \
        available agents, select the single best agent to handle the task.

        Respond with JSON only: {"agent": "<agent-name>", "reason": "<one sentence>"}

        Select based on the agent's capabilities, briefing, domain expertise, \
        and any historical evidence provided. Choose the agent whose skills \
        most closely match the task requirements, using historical evidence \
        to inform confidence when multiple agents are comparably qualified.""";
```

Update the `doSelect()` method — after building the prompt, before invoking:
```java
String enrichment = promptAssembler.assemble(context, eligible);
if (enrichment != null) {
    prompt = prompt + "\n\n" + enrichment;
}
```

Also update the existing constructor to accept `RoutingPromptAssembler` and update
existing tests to pass a no-op assembler (`new RoutingPromptAssembler(List.of())`).

- [ ] **Step 4: Run all LlmAgentRoutingStrategy tests**

Run: `mvn test -pl . -Dtest=LlmAgentRoutingStrategyTest`
Expected: all tests PASS (existing + new)

- [ ] **Step 5: Commit**

```
feat(#16): LlmAgentRoutingStrategy — composable prompt enrichment via assembler
```

---

### Task 5: CbrAgentRoutingStrategy — feature extractor adoption

**Files:**
- Modify: `src/main/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategy.java`
- Modify: `src/test/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategyTest.java`

**Interfaces:**
- Consumes: `RoutingFeatureExtractor` (Task 1)
- Produces: Updated `CbrAgentRoutingStrategy` using feature extractor for CBR queries

- [ ] **Step 1: Write test for feature extractor usage**

Add to `CbrAgentRoutingStrategyTest.java`:
```java
@Test
void usesFeatureExtractorForQuery() {
    var customExtractor = mock(RoutingFeatureExtractor.class);
    when(customExtractor.extractProblem(any())).thenReturn("custom problem");
    when(customExtractor.extractFeatures(any())).thenReturn(Map.of("domain", "aml"));
    when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
            .thenReturn(List.of());
    when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
            .thenReturn(List.of("agent-a"));

    var strategy = new CbrAgentRoutingStrategy(
            10, 0.5, presentInstance(cbrStore), presentInstance(graphQuery),
            absentInstance(), absentInstance(), absentInstance(),
            customExtractor);

    var result = strategy.select(context("analysis"),
            List.of(candidate("agent-a"))).await().indefinitely();

    assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
    var queryCaptor = org.mockito.ArgumentCaptor.forClass(CbrQuery.class);
    verify(cbrStore).retrieveSimilar(queryCaptor.capture(), eq(CbrCase.class));
    assertThat(queryCaptor.getValue().problem()).isEqualTo("custom problem");
    assertThat(queryCaptor.getValue().features()).containsEntry("domain", "aml");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl . -Dtest=CbrAgentRoutingStrategyTest`
Expected: compilation failure — constructor doesn't accept `RoutingFeatureExtractor`

- [ ] **Step 3: Update CbrAgentRoutingStrategy**

Add `RoutingFeatureExtractor` parameter to constructor. In `tryCbrStore()`, replace
the hardcoded `Map.of()` and inline `caseContext.toString()` with calls to the extractor:

```java
CbrQuery query = CbrQuery.of(
        context.tenancyId(),
        new MemoryDomain(context.capabilityName()),
        "agent-routing",
        featureExtractor.extractFeatures(context),
        topK)
        .withMinSimilarity(minSimilarity);
String problem = featureExtractor.extractProblem(context);
if (problem != null) {
    query = query.withProblem(problem);
}
```

Update existing tests to pass `new TextOnlyFeatureExtractor()` as the new constructor
parameter.

- [ ] **Step 4: Run all CbrAgentRoutingStrategy tests**

Run: `mvn test -pl . -Dtest=CbrAgentRoutingStrategyTest`
Expected: all tests PASS

- [ ] **Step 5: Commit**

```
feat(#16): CbrAgentRoutingStrategy — adopt RoutingFeatureExtractor for CBR queries
```

---

### Task 6: RoutingOutcomeRecorder SPI in engine-api

**Files:**
- Create: `api/src/main/java/io/casehub/api/spi/routing/RoutingOutcomeRecorder.java` (in engine repo)

**Interfaces:**
- Consumes: `AgentRoutingContext` (engine-api)
- Produces: `RoutingOutcomeRecorder` interface with `record(AgentRoutingContext, String workerId, String bindingName, String executionOutcome, @Nullable Duration executionDuration)` → `Uni<Void>`. Used by Tasks 7 and 8.

- [ ] **Step 1: Create the interface**

```java
package io.casehub.api.spi.routing;

import io.smallrye.mutiny.Uni;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public interface RoutingOutcomeRecorder {
    Uni<Void> record(AgentRoutingContext context, String workerId, String bindingName,
                     String executionOutcome, @Nullable Duration executionDuration);
}
```

- [ ] **Step 2: Verify engine-api compiles**

Run (from engine repo root): `mvn compile -pl api -DskipTests`
Expected: BUILD SUCCESS — no new dependencies needed

- [ ] **Step 3: Install engine-api to local Maven repo**

Run: `mvn install -pl api -DskipTests`
Expected: BUILD SUCCESS — blocks can now resolve the updated engine-api

- [ ] **Step 4: Commit**

In engine repo:
```
feat(blocks#16): RoutingOutcomeRecorder SPI — optional outcome recording for routing decisions
```

---

### Task 7: CbrRoutingOutcomeRecorder in blocks

**Files:**
- Create: `src/main/java/io/casehub/blocks/routing/agent/CbrRoutingOutcomeRecorder.java`
- Test: `src/test/java/io/casehub/blocks/routing/agent/CbrRoutingOutcomeRecorderTest.java`

**Interfaces:**
- Consumes: `RoutingOutcomeRecorder` (Task 6), `RoutingFeatureExtractor` (Task 1), `CbrCaseMemoryStore` (neural-text)
- Produces: `CbrRoutingOutcomeRecorder` — CDI bean that records routing outcomes as `PlanCbrCase` entries in the CBR store

- [ ] **Step 1: Write the failing tests**

```java
package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CbrRoutingOutcomeRecorderTest {

    @Mock CbrCaseMemoryStore cbrStore;

    private final RoutingFeatureExtractor extractor = new TextOnlyFeatureExtractor();
    private CbrRoutingOutcomeRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new CbrRoutingOutcomeRecorder(cbrStore, extractor);
    }

    private AgentRoutingContext context() {
        return new AgentRoutingContext(UUID.randomUUID(), "analysis", NullNode.instance, "t");
    }

    @Test
    void recordsSuccess_asPlanCbrCase() {
        when(cbrStore.store(any(), anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn("stored-id");

        recorder.record(context(), "agent-a", "binding-x", "SUCCESS", null)
                .await().indefinitely();

        var caseCaptor = ArgumentCaptor.forClass(CbrCase.class);
        verify(cbrStore).store(caseCaptor.capture(), anyString(),
                eq("agent-routing"), any(MemoryDomain.class), eq("t"), anyString());

        var stored = (PlanCbrCase) caseCaptor.getValue();
        assertThat(stored.outcome()).isEqualTo("SUCCESS");
        assertThat(stored.planTrace()).hasSize(1);
        assertThat(stored.planTrace().getFirst().workerName()).isEqualTo("agent-a");
        assertThat(stored.planTrace().getFirst().bindingName()).isEqualTo("binding-x");
        assertThat(stored.planTrace().getFirst().capabilityName()).isEqualTo("analysis");
        assertThat(stored.planTrace().getFirst().stepOutcome()).isEqualTo("SUCCESS");
    }

    @Test
    void recordsFailure() {
        when(cbrStore.store(any(), anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn("stored-id");

        recorder.record(context(), "agent-b", "binding-y", "FAILURE", null)
                .await().indefinitely();

        var caseCaptor = ArgumentCaptor.forClass(CbrCase.class);
        verify(cbrStore).store(caseCaptor.capture(), anyString(),
                eq("agent-routing"), any(MemoryDomain.class), anyString(), anyString());

        var stored = (PlanCbrCase) caseCaptor.getValue();
        assertThat(stored.outcome()).isEqualTo("FAILURE");
        assertThat(stored.planTrace().getFirst().stepOutcome()).isEqualTo("FAILURE");
    }

    @Test
    void nullCbrStore_completesSuccessfully() {
        var noStore = new CbrRoutingOutcomeRecorder(null, extractor);
        noStore.record(context(), "a", "b", "SUCCESS", null).await().indefinitely();
        // no exception, no store call
    }

    @Test
    void usesCapabilityNameAsFallbackProblem() {
        when(cbrStore.store(any(), anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn("stored-id");
        // NullNode context → extractProblem returns null → falls back to capabilityName
        recorder.record(context(), "a", "b", "SUCCESS", null).await().indefinitely();

        var caseCaptor = ArgumentCaptor.forClass(CbrCase.class);
        verify(cbrStore).store(caseCaptor.capture(), anyString(), anyString(),
                any(), anyString(), anyString());
        assertThat(((PlanCbrCase) caseCaptor.getValue()).problem()).isEqualTo("analysis");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl . -Dtest=CbrRoutingOutcomeRecorderTest -DfailIfNoTests=false`
Expected: compilation failure

- [ ] **Step 3: Write the implementation**

`CbrRoutingOutcomeRecorder.java`:
```java
package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.RoutingOutcomeRecorder;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class CbrRoutingOutcomeRecorder implements RoutingOutcomeRecorder {

    private static final System.Logger LOG =
            System.getLogger(CbrRoutingOutcomeRecorder.class.getName());

    private final @Nullable CbrCaseMemoryStore cbrStore;
    private final RoutingFeatureExtractor featureExtractor;

    @Inject
    public CbrRoutingOutcomeRecorder(
            Instance<CbrCaseMemoryStore> cbrStore,
            Instance<RoutingFeatureExtractor> featureExtractor) {
        this.cbrStore = cbrStore.isUnsatisfied() ? null : cbrStore.get();
        this.featureExtractor = featureExtractor.isUnsatisfied()
                ? new TextOnlyFeatureExtractor() : featureExtractor.get();
    }

    CbrRoutingOutcomeRecorder(@Nullable CbrCaseMemoryStore cbrStore,
                              RoutingFeatureExtractor featureExtractor) {
        this.cbrStore = cbrStore;
        this.featureExtractor = featureExtractor;
    }

    @Override
    public Uni<Void> record(AgentRoutingContext context, String workerId,
                            String bindingName, String executionOutcome,
                            @Nullable Duration executionDuration) {
        if (cbrStore == null) return Uni.createFrom().voidItem();

        return Uni.createFrom().item(() -> {
            var trace = new PlanTrace(
                    bindingName, context.capabilityName(),
                    workerId, executionOutcome, 0, Map.of());
            var problem = featureExtractor.extractProblem(context);
            var cbrCase = new PlanCbrCase(
                    problem != null ? problem : context.capabilityName(),
                    "Routed to " + workerId,
                    executionOutcome, null,
                    featureExtractor.extractFeatures(context),
                    List.of(trace));
            cbrStore.store(cbrCase, context.caseId().toString(),
                    "agent-routing", new MemoryDomain(context.capabilityName()),
                    context.tenancyId(), context.caseId().toString());
            return null;
        }).emitOn(Infrastructure.getDefaultWorkerPool())
          .replaceWithVoid();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl . -Dtest=CbrRoutingOutcomeRecorderTest`
Expected: all 4 tests PASS

- [ ] **Step 5: Commit**

```
feat(#16): CbrRoutingOutcomeRecorder — records routing outcomes to CBR store
```

---

### Task 8: Engine runtime integration — WorkflowExecutionCompletedHandler

**Files:**
- Modify: `runtime/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java` (in engine repo)

**Interfaces:**
- Consumes: `RoutingOutcomeRecorder` (Task 6), `AgentRoutingContext` (engine-api)
- Produces: Engine calls `RoutingOutcomeRecorder.record()` at worker completion for both success and failure paths. PlannedAction events are skipped.

- [ ] **Step 1: Add the recorder injection and success-path integration**

In `WorkflowExecutionCompletedHandler`, add field:
```java
@Inject Instance<io.casehub.api.spi.routing.RoutingOutcomeRecorder> outcomeRecorder;
```

In `onWorkflowExecutionCompletedHandler()`, after line 125 (`JsonNode contextBefore = ...`)
and before `applyOutputWithConflictResolution()`, capture the context snapshot for recording:
```java
JsonNode contextForRecording = contextBefore;
```

After line 130 (`recordSuccessOutcome(...)`) and before the settlement recording block,
add the recorder call:
```java
fireOutcomeRecorder(caseInstance, worker, bindingName, "SUCCESS", contextForRecording);
```

- [ ] **Step 2: Add the failure-path integration**

In `handleSemanticFailure()`, after the outcome status is determined (around line 323)
and before the outcome recording/event publishing chain, capture the context and fire:
```java
JsonNode contextForRecording = caseInstance.getCaseContext()
        .snapshot().asJsonNode();
fireOutcomeRecorder(caseInstance, worker, bindingName, "FAILURE", contextForRecording);
```

- [ ] **Step 3: Add the shared recording method**

```java
private void fireOutcomeRecorder(CaseInstance caseInstance, Worker worker,
                                  String bindingName, String outcomeString,
                                  JsonNode contextSnapshot) {
    if (outcomeRecorder.isUnsatisfied()) return;
    String capabilityName = extractCapabilityTag(caseInstance, worker, bindingName);
    if (capabilityName == null) return;
    var ctx = new io.casehub.api.spi.routing.AgentRoutingContext(
            caseInstance.getUuid(), capabilityName,
            contextSnapshot, caseInstance.tenancyId);
    outcomeRecorder.get()
            .record(ctx, worker.name(), bindingName, outcomeString, null)
            .subscribe().with(
                    ignored -> {},
                    err -> LOG.warnf(err,
                            "Outcome recording failed for caseId=%s worker=%s",
                            caseInstance.getUuid(), worker.name()));
}
```

- [ ] **Step 4: Verify engine compiles**

Run (from engine repo root): `mvn compile -pl runtime -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 5: Run existing engine tests to verify no regressions**

Run: `mvn test -pl runtime -Dtest=WorkflowExecutionCompletedHandlerTest`
Expected: all existing tests PASS — the `Instance<RoutingOutcomeRecorder>` is
unsatisfied in tests (no mock registered), so `fireOutcomeRecorder` is a no-op.

- [ ] **Step 6: Install engine to local Maven repo**

Run: `mvn install -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

In engine repo:
```
feat(blocks#16): engine records routing outcomes via RoutingOutcomeRecorder SPI
```

---

## Final Verification

After all tasks complete:

- [ ] Run full blocks test suite: `mvn test`
- [ ] Run full engine test suite: `mvn test` (from engine repo)
- [ ] Verify the feedback loop end-to-end: outcome recording → CBR store → prompt enrichment
