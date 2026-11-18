# Unify Prompt Assembly Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #1105 — Unify prompt assembly across eidos, neocortex, and agency
**Issue group:** #1105

**Goal:** Add a `CognitiveLifecycle` SPI to cognition-api that CognitionCore implements, exposing cognitive prompt sections as eidos `PromptBlock`s via a bridge adapter.

**Architecture:** New `CognitiveLifecycle` interface in cognition-api with 4 lifecycle methods + `promptSections()` + a default `contributeBlocks()` bridge method that wraps `CognitionPromptRenderer` output into `PromptBlock(COGNITIVE, tag, 0f, content)`. CognitionCore adds `implements CognitiveLifecycle` with no internal changes — all required methods already exist.

**Tech Stack:** Java 21, Quarkus 3.32.2, eidos-api (already a dependency of cognition-api)

## Global Constraints

- eidos-api is already a dependency of cognition-api — no new dependency edges
- `CognitionPromptRenderer` interface is unchanged — no blast radius to 24 renderer implementations
- `BlockTag` enum is unchanged
- All new code in cognition-api (the SPI) and cognition (the implementation)
- `PromptBlock` and `PromptTier` from `io.casehub.eidos.api`

---

## Batch 1: CognitiveLifecycle SPI and bridge

### Task 1: Create CognitiveLifecycle interface with contributeBlocks() default method

**Files:**
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/prompt/CognitiveLifecycle.java`
- Test: `cognition-api/src/test/java/io/casehub/neocortex/cognition/prompt/CognitiveLifecycleContributeBlocksTest.java`

**Interfaces:**
- Consumes: `CognitionPromptRenderer` (render → @Nullable String, blockTag → @Nullable BlockTag), `CognitionRenderContext`, `BlockTag`, `PromptBlock` (eidos-api), `PromptTier` (eidos-api)
- Produces: `CognitiveLifecycle` interface — used by Task 2 (`CognitionCore implements CognitiveLifecycle`) and downstream by agency#490

- [ ] **Step 1: Write failing tests for contributeBlocks() bridge logic**

Create `CognitiveLifecycleContributeBlocksTest.java`:

```java
package io.casehub.neocortex.cognition.prompt;

import io.casehub.eidos.api.PromptBlock;
import io.casehub.eidos.api.PromptTier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveLifecycleContributeBlocksTest {

    private static CognitiveLifecycle lifecycleWith(List<CognitionPromptRenderer> sections) {
        return new CognitiveLifecycle() {
            @Override
            public void tick(String agentId, String tenantId,
                             Set<String> activeSubjects) {}

            @Override
            public void recordInteraction(String agentId, String tenantId,
                                          @Nullable String subjectId,
                                          String userMessage, String response) {}

            @Override
            public List<CognitionPromptRenderer> promptSections() {
                return sections;
            }
        };
    }

    @Test
    void wrapsRendererOutputInCognitivePromptBlock() {
        var renderer = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return "You feel calm.";
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.MOOD;
            }
        };
        var lifecycle = lifecycleWith(List.of(renderer));
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);

        var blocks = lifecycle.contributeBlocks(ctx);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).tier()).isEqualTo(PromptTier.COGNITIVE);
        assertThat(blocks.get(0).tag()).isEqualTo("MOOD");
        assertThat(blocks.get(0).salience()).isEqualTo(0f);
        assertThat(blocks.get(0).content()).isEqualTo("You feel calm.");
    }

    @Test
    void skipsNullRendererOutput() {
        var renderer = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return null;
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.MOOD;
            }
        };
        var lifecycle = lifecycleWith(List.of(renderer));
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);

        var blocks = lifecycle.contributeBlocks(ctx);

        assertThat(blocks).isEmpty();
    }

    @Test
    void skipsBlankRendererOutput() {
        var renderer = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return "   ";
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.MOOD;
            }
        };
        var lifecycle = lifecycleWith(List.of(renderer));
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);

        var blocks = lifecycle.contributeBlocks(ctx);

        assertThat(blocks).isEmpty();
    }

    @Test
    void usesClassSimpleNameWhenBlockTagIsNull() {
        var renderer = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return "Some content.";
            }
        };
        var lifecycle = lifecycleWith(List.of(renderer));
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);

        var blocks = lifecycle.contributeBlocks(ctx);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).tag()).isNotNull();
        assertThat(blocks.get(0).tier()).isEqualTo(PromptTier.COGNITIVE);
    }

    @Test
    void preservesInsertionOrderAcrossMultipleRenderers() {
        var mood = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return "Mood content.";
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.MOOD;
            }
        };
        var drives = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return "Drive content.";
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.DRIVES;
            }
        };
        var nullSection = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return null;
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.NARRATIVE;
            }
        };
        var lifecycle = lifecycleWith(List.of(mood, nullSection, drives));
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);

        var blocks = lifecycle.contributeBlocks(ctx);

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).tag()).isEqualTo("MOOD");
        assertThat(blocks.get(1).tag()).isEqualTo("DRIVES");
    }

    @Test
    void returnsUnmodifiableList() {
        var renderer = new CognitionPromptRenderer() {
            @Override
            public @Nullable String render(CognitionRenderContext context) {
                return "Content.";
            }

            @Override
            public BlockTag blockTag() {
                return BlockTag.MOOD;
            }
        };
        var lifecycle = lifecycleWith(List.of(renderer));
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);

        var blocks = lifecycle.contributeBlocks(ctx);

        assertThat(blocks).isUnmodifiable();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition-api -Dtest=CognitiveLifecycleContributeBlocksTest -DfailIfNoTests=false`
Expected: Compilation failure — `CognitiveLifecycle` does not exist yet.

- [ ] **Step 3: Create CognitiveLifecycle interface**

Create `cognition-api/src/main/java/io/casehub/neocortex/cognition/prompt/CognitiveLifecycle.java`:

```java
package io.casehub.neocortex.cognition.prompt;

import io.casehub.eidos.api.PromptBlock;
import io.casehub.eidos.api.PromptTier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public interface CognitiveLifecycle {

    default void initialize(String agentId, String tenantId) {}

    void tick(String agentId, String tenantId, Set<String> activeSubjects);

    default @Nullable String evaluateProactive(String agentId, String tenantId,
                                                String channelContext) {
        return null;
    }

    void recordInteraction(String agentId, String tenantId,
                           @Nullable String subjectId,
                           String userMessage, String response);

    List<CognitionPromptRenderer> promptSections();

    default List<PromptBlock> contributeBlocks(CognitionRenderContext context) {
        var blocks = new ArrayList<PromptBlock>();
        for (var renderer : promptSections()) {
            try {
                var content = renderer.render(context);
                if (content != null && !content.isBlank()) {
                    var tag = renderer.blockTag();
                    var tagName = tag != null ? tag.name() : renderer.getClass().getSimpleName();
                    blocks.add(new PromptBlock(PromptTier.COGNITIVE, tagName, 0f, content));
                }
            } catch (Exception e) {
                // skip sections with unresolvable dependencies — matches CognitionCore behaviour
            }
        }
        return List.copyOf(blocks);
    }
}
```

Design notes for the implementer:
- `initialize()` and `evaluateProactive()` have default implementations (no-op / return null) because CognitionCore's versions require additional context (AgentDescriptor, AgentRegistry) that varies by caller. Agency's adapter overrides these.
- `tick()` and `recordInteraction()` are abstract — every implementation must provide them.
- `contributeBlocks()` has a try/catch per renderer matching `CognitionCore.java:1170-1181` where renderer exceptions are silently skipped.
- The returned list is an unmodifiable copy via `List.copyOf()`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition-api -Dtest=CognitiveLifecycleContributeBlocksTest`
Expected: All 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add cognition-api/src/main/java/io/casehub/neocortex/cognition/prompt/CognitiveLifecycle.java cognition-api/src/test/java/io/casehub/neocortex/cognition/prompt/CognitiveLifecycleContributeBlocksTest.java
git commit -m "feat: add CognitiveLifecycle SPI with contributeBlocks() bridge  Refs #1105"
```

### Task 2: CognitionCore implements CognitiveLifecycle

**Files:**
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java:72` (class declaration)

**Interfaces:**
- Consumes: `CognitiveLifecycle` (from Task 1)
- Produces: `CognitionCore` now satisfies the `CognitiveLifecycle` contract — agency#490 can depend on this

CognitionCore already has `promptSections()` and `recordInteraction()`. It needs to implement `tick(String, String, Set<String>)` — the simplified lifecycle tick that wraps the internal `tick(agentId, tenantId, descriptor, resolver, ...)` using cached state.

- [ ] **Step 1: Write failing test verifying CognitionCore is a CognitiveLifecycle**

Create `cognition/src/test/java/io/casehub/neocortex/cognition/core/CognitionCoreCognitiveLifecycleTest.java`:

```java
package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.prompt.CognitiveLifecycle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionCoreCognitiveLifecycleTest {

    @Test
    void implementsCognitiveLifecycle() {
        assertThat(CognitiveLifecycle.class).isAssignableFrom(CognitionCore.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CognitionCoreCognitiveLifecycleTest -DfailIfNoTests=false`
Expected: FAIL — CognitionCore does not implement CognitiveLifecycle.

- [ ] **Step 3: Add implements clause and bridge tick method**

Modify `CognitionCore.java` line 72. Change:

```java
public class CognitionCore implements io.casehub.neocortex.cognition.trace.CognitionStateProvider {
```

to:

```java
public class CognitionCore implements CognitiveLifecycle, io.casehub.neocortex.cognition.trace.CognitionStateProvider {
```

Add the import at the top of the file:

```java
import io.casehub.neocortex.cognition.prompt.CognitiveLifecycle;
```

Add the simplified lifecycle tick method (after the existing tick overloads, around line 239):

```java
    @Override
    public void tick(String agentId, String tenantId, Set<String> activeSubjects) {
        tick(agentId, tenantId, lastDescriptor, (aid, tid) -> activeSubjects);
    }

    @Override
    public void recordInteraction(String agentId, String tenantId,
                                  @Nullable String subjectId,
                                  String userMessage, String response) {
        recordInteraction(agentId, tenantId, subjectId, userMessage, response, null);
    }
```

Note: The existing `recordInteraction` takes 6 parameters (including `@Nullable CognitiveImpact impact`). The `CognitiveLifecycle` interface declares a 5-parameter version. This override delegates to the 6-parameter version with `null` impact.

The existing `promptSections()` already returns `List<CognitionPromptRenderer>` and satisfies the interface contract.

`initialize()` uses the default no-op from the interface — CognitionAvatarAdapter's initialize calls `core.tick(...)` directly, so the no-op default is correct.

`evaluateProactive()` uses the default null-return from the interface — ProactiveSpeechSupport requires AgentRegistry (agency concern), so the default is correct.

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CognitionCoreCognitiveLifecycleTest`
Expected: PASS.

- [ ] **Step 5: Run full module tests to verify no regressions**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition-api,cognition`
Expected: All existing tests pass.

- [ ] **Step 6: Commit**

```bash
git add cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java cognition/src/test/java/io/casehub/neocortex/cognition/core/CognitionCoreCognitiveLifecycleTest.java
git commit -m "feat: CognitionCore implements CognitiveLifecycle  Refs #1105"
```

### Task 3: Integration test — contributeBlocks() through CognitionCore

**Files:**
- Test: `cognition/src/test/java/io/casehub/neocortex/cognition/core/CognitionCoreContributeBlocksTest.java`

**Interfaces:**
- Consumes: `CognitiveLifecycle.contributeBlocks()` (from Task 1), `CognitionCore` (from Task 2)
- Produces: Verification that the full pipeline works end-to-end

- [ ] **Step 1: Write integration test**

Create `cognition/src/test/java/io/casehub/neocortex/cognition/core/CognitionCoreContributeBlocksTest.java`:

```java
package io.casehub.neocortex.cognition.core;

import io.casehub.eidos.api.PromptTier;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.CognitiveLifecycle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionCoreContributeBlocksTest {

    @Test
    void contributeBlocksProducesBlocksFromPromptSections() {
        var config = CognitionConfig.none().with("mood", true).with("directivePrompts", true);
        var core = new CognitionCore(config);

        core.tick("agent-1", "tenant-1", null, (aid, tid) -> java.util.Set.of());

        CognitiveLifecycle lifecycle = core;
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);
        var blocks = lifecycle.contributeBlocks(ctx);

        for (var block : blocks) {
            assertThat(block.tier()).isEqualTo(PromptTier.COGNITIVE);
            assertThat(block.tag()).isNotNull();
            assertThat(block.content()).isNotBlank();
        }
    }

    @Test
    void contributeBlocksReturnsEmptyWhenAllSectionsDisabled() {
        var config = CognitionConfig.none();
        var core = new CognitionCore(config);
        core.tick("agent-1", "tenant-1", null, (aid, tid) -> java.util.Set.of());

        CognitiveLifecycle lifecycle = core;
        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);
        var blocks = lifecycle.contributeBlocks(ctx);

        assertThat(blocks).isEmpty();
    }
}
```

The `CognitionConfig` record has 26 boolean fields with factory methods: `none()` (all false), `all()` (sensible defaults), `withAllSubsystems()` (everything on). Use `CognitionConfig.none().with("field", true)` for selective enablement. The test verifies:
1. CognitionCore can be used as CognitiveLifecycle
2. `contributeBlocks()` produces COGNITIVE-tier blocks
3. Empty config produces no blocks

- [ ] **Step 2: Run test**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CognitionCoreContributeBlocksTest`
Expected: PASS (the interface and implementation are already in place from Tasks 1-2).

- [ ] **Step 3: Commit**

```bash
git add cognition/src/test/java/io/casehub/neocortex/cognition/core/CognitionCoreContributeBlocksTest.java
git commit -m "test: integration test for CognitionCore.contributeBlocks()  Refs #1105"
```

## References

- [specs/issue-1105-unify-prompt-assembly/2026-11-18-unify-prompt-assembly-design.md] — design spec this plan implements
- [cognition-api/src/main/java/io/casehub/neocortex/cognition/prompt/CognitionPromptRenderer.java] — existing renderer SPI (unchanged)
- [cognition-api/src/main/java/io/casehub/neocortex/cognition/prompt/BlockTag.java] — 24-entry ordering enum (unchanged)
- [cognition-api/src/main/java/io/casehub/neocortex/cognition/prompt/CognitionRenderContext.java] — render context record (unchanged)
- [cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java:72,240-245,423-426,746-828] — class declaration, tick overloads, recordInteraction, promptSections
- [cognition/src/main/java/io/casehub/neocortex/cognition/prompt/DirectiveSection.java] — existing tag-resolution pattern
- [eidos api/src/main/java/io/casehub/eidos/api/PromptBlock.java] — target PromptBlock type
- [eidos api/src/main/java/io/casehub/eidos/api/PromptTier.java] — 4-tier enum
- [agency agency-core/src/main/java/io/casehub/agency/social/cognition/CognitionAvatarAdapter.java] — current bridge (downstream consumer)
- [GitHub #1105] — master issue
- [GitHub eidos#240] — AssembledPrompt model field prerequisite
- [GitHub agency#490] — downstream agency adoption
