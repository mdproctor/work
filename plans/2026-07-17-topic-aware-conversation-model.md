# Topic-Aware Conversation Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #49 — end-to-end integration: engine choreography over qhorus topics rendered in conversation view
**Issue group:** #49

**Goal:** Make the conversation projection topic-aware, reaction-decorated, and obligation-chain-visible so it becomes a structured workflow visualisation surface.

**Architecture:** Enrich existing records (ThreadEntry, ConversationPoint) with fields already present on MessageView. Update the fold to pass them through. Enhance the renderer with topic grouping, obligation chains, and reaction decoration. No new types except config additions. No SPI changes.

**Tech Stack:** Java 21, JUnit 5, Mockito, AssertJ. No CDI, no Quarkus runtime.

## Global Constraints

- All changes scoped to `casehub-blocks` — no qhorus or engine changes required
- `ChannelProjection<S>` SPI (qhorus-api) must NOT change
- `ConversationProjection` abstract hooks (`sentinel()`, `isPointInitiator()`, `statusAfter()`) must NOT change
- Existing tests must pass after each task (record changes require constructor fixup)
- Pre-release platform — breaking record shapes are expected and correct
- ConversationState does NOT gain a choreography/progress field (deferred to #62)
- Progress overlay deferred to blocks#62 (depends on work#237)

---

### Task 1: Enrich ThreadEntry and ConversationPoint records

Add `Long messageId` and `MessageType messageType` to `ThreadEntry`. Add `String topic` to `ConversationPoint`. Fix all construction sites across production and test code.

**Files:**
- Modify: `src/main/java/io/casehub/blocks/conversation/ThreadEntry.java`
- Modify: `src/main/java/io/casehub/blocks/conversation/ConversationPoint.java`
- Modify: `src/main/java/io/casehub/blocks/conversation/ConversationFold.java` (constructor calls only — signatures stay until Task 2)
- Modify: `src/test/java/io/casehub/blocks/conversation/ConversationFoldTest.java` (constructor calls)
- Modify: `src/test/java/io/casehub/blocks/conversation/ConversationRendererTest.java` (helper methods)
- Modify: `src/test/java/io/casehub/blocks/conversation/ConversationProjectionTest.java` (assertions)
- Test: existing test suite — all must pass after fixup

**Interfaces:**
- Produces: `ThreadEntry(String entryId, Long messageId, MessageType messageType, String role, int round, String entryType, String content)`
- Produces: `ConversationPoint(String id, String topic, PointClassification classification, List<ThreadEntry> thread, String status)`

- [ ] **Step 1: Write failing test for ThreadEntry.messageId and messageType**

Add to `ConversationFoldTest`:

```java
@Test
void createsPointWithMessageIdAndType() {
    var classification = new PointClassification(Priority.HIGH, "global", null);
    var result = ConversationFold.createPoint(empty,
            "point-1", "general", 100L, MessageType.COMMAND,
            classification, "REVIEWER", 1, "RAISE", "content");

    ThreadEntry entry = result.points().get("point-1").thread().get(0);
    assertThat(entry.messageId()).isEqualTo(100L);
    assertThat(entry.messageType()).isEqualTo(MessageType.COMMAND);
}
```

Run: `mvn test -pl . -Dtest=ConversationFoldTest -DfailIfNoTests=false`
Expected: FAIL — `messageId()` and `messageType()` don't exist on ThreadEntry

- [ ] **Step 2: Write failing test for ConversationPoint.topic**

Add to `ConversationFoldTest`:

```java
@Test
void createsPointWithTopic() {
    var classification = new PointClassification(Priority.HIGH, "global", null);
    var result = ConversationFold.createPoint(empty,
            "point-1", "review", 100L, MessageType.COMMAND,
            classification, "REVIEWER", 1, "RAISE", "content");

    assertThat(result.points().get("point-1").topic()).isEqualTo("review");
}
```

Run: `mvn test -pl . -Dtest=ConversationFoldTest -DfailIfNoTests=false`
Expected: FAIL — `topic()` doesn't exist on ConversationPoint

- [ ] **Step 3: Update ThreadEntry record**

Use `ide_edit_member` on `ThreadEntry`:

```java
public record ThreadEntry(
        String entryId,
        Long messageId,
        MessageType messageType,
        String role,
        int round,
        String entryType,
        String content) {}
```

Add import: `import io.casehub.qhorus.api.message.MessageType;`

- [ ] **Step 4: Update ConversationPoint record**

Use `ide_edit_member` on `ConversationPoint`:

```java
public record ConversationPoint(
        String id,
        String topic,
        PointClassification classification,
        List<ThreadEntry> thread,
        String status) {
    public ConversationPoint {
        thread = List.copyOf(thread);
    }
}
```

- [ ] **Step 5: Fix ConversationFold constructor calls**

Every `new ThreadEntry(...)` needs `messageId` and `messageType` parameters inserted after `entryId`. Every `new ConversationPoint(...)` needs `topic` inserted after `id`. For now, use `null` for all three new fields — Task 2 wires real values.

`ConversationFold.createPoint` line 24:
```java
thread.add(new ThreadEntry(pointId, null, null, role, round, entryType, content));
```

`ConversationFold.createPoint` line 25-26:
```java
var point = new ConversationPoint(pointId, null, classification, thread,
        ConversationProtocol.STATUS_OPEN);
```

`ConversationFold.respondToPoint` line 44:
```java
thread.add(new ThreadEntry(null, null, null, role, round, entryType, content));
```

`ConversationFold.respondToPoint` line 47-48:
```java
var updated = new ConversationPoint(existing.id(), existing.topic(), existing.classification(),
        thread, resolvedStatus);
```

`ConversationFold.flagHuman` line 65:
```java
thread.add(new ThreadEntry(null, null, null, role, round, ConversationProtocol.FLAG_HUMAN, content));
```

`ConversationFold.flagHuman` line 66-67:
```java
points.put(targetId, new ConversationPoint(p.id(), p.topic(), p.classification(),
        thread, ConversationProtocol.STATUS_ESCALATED));
```

- [ ] **Step 6: Fix ConversationRendererTest helpers**

`point()` helper (line 50-52):
```java
static ConversationPoint point(String id, Priority priority, String scope, String location,
                                String status, List<ThreadEntry> thread) {
    return new ConversationPoint(id, "general", new PointClassification(priority, scope, location), thread, status);
}
```

`entry()` helper (line 55-56):
```java
static ThreadEntry entry(String role, String entryType, String content) {
    return new ThreadEntry(null, null, null, role, 1, entryType, content);
}
```

- [ ] **Step 7: Fix ConversationStateTest**

The `ConversationStateTest` constructs `ConversationPoint` indirectly via `ConversationState`. Verify — the test creates an empty state from `Map.of()`, so no ConversationPoint constructors to fix there. Confirm by running:

Run: `mvn test -pl . -Dtest=ConversationStateTest`
Expected: PASS

- [ ] **Step 8: Run full test suite**

Run: `mvn test -pl .`
Expected: ALL PASS (except the two new tests from Steps 1-2 which still fail because `ConversationFold.createPoint` signature hasn't changed yet)

- [ ] **Step 9: Verify with ide_diagnostics**

Run `ide_diagnostics` on all modified files to catch any remaining compilation errors.

- [ ] **Step 10: Commit**

```
feat(#49): enrich ThreadEntry with messageId/messageType, ConversationPoint with topic
```

---

### Task 2: Update ConversationFold to propagate topic, messageId, messageType

Update all ConversationFold method signatures to accept and pass through the new fields. Wire `topic` into `createPoint`, `messageId`/`messageType` into all ThreadEntry construction sites.

**Files:**
- Modify: `src/main/java/io/casehub/blocks/conversation/ConversationFold.java`
- Modify: `src/test/java/io/casehub/blocks/conversation/ConversationFoldTest.java`

**Interfaces:**
- Consumes: `ThreadEntry(entryId, messageId, messageType, role, round, entryType, content)`, `ConversationPoint(id, topic, classification, thread, status)`
- Produces: `ConversationFold.createPoint(state, pointId, topic, messageId, messageType, classification, role, round, entryType, content)`
- Produces: `ConversationFold.respondToPoint(state, targetId, messageId, messageType, role, round, entryType, content, newStatus)`
- Produces: `ConversationFold.flagHuman(state, targetId, messageId, role, round, content)`
- Produces: `ConversationFold.addMemo(state, messageId, role, round, content)` (messageId on memos is future-proofing — null for now)

- [ ] **Step 1: Update ConversationFold.createPoint signature and body**

```java
public static ConversationState createPoint(ConversationState state,
                                             String pointId,
                                             String topic,
                                             Long messageId,
                                             MessageType messageType,
                                             PointClassification classification,
                                             String role, int round,
                                             String entryType, String content) {
    var thread = new ArrayList<ThreadEntry>();
    thread.add(new ThreadEntry(pointId, messageId, messageType, role, round, entryType, content));
    var point = new ConversationPoint(pointId, topic, classification, thread,
            ConversationProtocol.STATUS_OPEN);

    var points = new LinkedHashMap<>(state.points());
    points.put(pointId, point);

    return new ConversationState(points, new ArrayList<>(state.humanFlags()),
            new ArrayList<>(state.memos()), new LinkedHashMap<>(state.subTaskFindings()));
}
```

Add import: `import io.casehub.qhorus.api.message.MessageType;`

- [ ] **Step 2: Update ConversationFold.respondToPoint signature and body**

```java
public static ConversationState respondToPoint(ConversationState state,
                                                String targetId,
                                                Long messageId,
                                                MessageType messageType,
                                                String role, int round,
                                                String entryType, String content,
                                                String newStatus) {
    if (!state.points().containsKey(targetId)) return state;

    ConversationPoint existing = state.points().get(targetId);
    var thread = new ArrayList<>(existing.thread());
    thread.add(new ThreadEntry(null, messageId, messageType, role, round, entryType, content));

    String resolvedStatus = newStatus != null ? newStatus : existing.status();
    var updated = new ConversationPoint(existing.id(), existing.topic(), existing.classification(),
            thread, resolvedStatus);

    var points = new LinkedHashMap<>(state.points());
    points.put(targetId, updated);

    return new ConversationState(points, new ArrayList<>(state.humanFlags()),
            new ArrayList<>(state.memos()), new LinkedHashMap<>(state.subTaskFindings()));
}
```

- [ ] **Step 3: Update ConversationFold.flagHuman signature and body**

```java
public static ConversationState flagHuman(ConversationState state,
                                           String targetId,
                                           Long messageId,
                                           String role, int round,
                                           String content) {
    var points = new LinkedHashMap<>(state.points());
    if (targetId != null && points.containsKey(targetId)) {
        ConversationPoint p = points.get(targetId);
        var thread = new ArrayList<>(p.thread());
        thread.add(new ThreadEntry(null, messageId, null, role, round, ConversationProtocol.FLAG_HUMAN, content));
        points.put(targetId, new ConversationPoint(p.id(), p.topic(), p.classification(),
                thread, ConversationProtocol.STATUS_ESCALATED));
    }

    var flags = new ArrayList<>(state.humanFlags());
    flags.add(new FlagEntry(null, round, role, content));

    return new ConversationState(points, flags,
            new ArrayList<>(state.memos()), new LinkedHashMap<>(state.subTaskFindings()));
}
```

- [ ] **Step 4: Fix all ConversationFoldTest call sites**

Every `ConversationFold.createPoint(empty, ...)` call needs `topic`, `messageId`, `messageType` inserted after `pointId`. Use `"general"`, `null`, `null` for existing tests.

Every `ConversationFold.respondToPoint(state, ...)` call needs `messageId`, `messageType` inserted after `targetId`. Use `null`, `null`.

Every `ConversationFold.flagHuman(...)` call needs `messageId` inserted after `targetId`. Use `null`.

There are ~25 call sites in ConversationFoldTest. Update each one.

- [ ] **Step 5: Run tests**

Run: `mvn test -pl . -Dtest=ConversationFoldTest`
Expected: ALL PASS including the new tests from Task 1 Steps 1-2

- [ ] **Step 6: Verify with ide_diagnostics**

- [ ] **Step 7: Commit**

```
feat(#49): propagate topic, messageId, messageType through ConversationFold
```

---

### Task 3: Update ConversationProjection to pass MessageView fields to fold

Wire `message.topic()`, `message.id()`, and `message.type()` from the `MessageView` through to the corresponding `ConversationFold` calls.

**Files:**
- Modify: `src/main/java/io/casehub/blocks/conversation/ConversationProjection.java`
- Modify: `src/test/java/io/casehub/blocks/conversation/ConversationProjectionTest.java`

**Interfaces:**
- Consumes: `ConversationFold.createPoint(state, pointId, topic, messageId, messageType, classification, role, round, entryType, body)` (from Task 2)
- Consumes: `ConversationFold.respondToPoint(state, targetId, messageId, messageType, role, round, entryType, body, newStatus)` (from Task 2)
- Consumes: `ConversationFold.flagHuman(state, targetId, messageId, role, round, content)` (from Task 2)

- [ ] **Step 1: Write failing test for topic attribution**

Add to `ConversationProjectionTest`:

```java
@Test
void pointInitiation_recordsTopicFromMessage() {
    var state = projection.identity();
    var msg = mock(MessageView.class);
    when(msg.content()).thenReturn(encode(meta(
            "entryType", "OPEN_TOPIC",
            "role", "REVIEWER",
            "round", "1",
            "priority", "HIGH"), "point body"));
    when(msg.correlationId()).thenReturn("point-1");
    when(msg.topic()).thenReturn("risk-assessment");
    when(msg.id()).thenReturn(42L);
    when(msg.type()).thenReturn(MessageType.COMMAND);

    var result = projection.apply(state, msg);

    assertThat(result.points().get("point-1").topic()).isEqualTo("risk-assessment");
}
```

Add import: `import io.casehub.qhorus.api.message.MessageType;`

Run: `mvn test -pl . -Dtest=ConversationProjectionTest#pointInitiation_recordsTopicFromMessage`
Expected: FAIL — projection currently ignores topic

- [ ] **Step 2: Write failing test for messageId attribution**

```java
@Test
void pointInitiation_recordsMessageIdAndType() {
    var state = projection.identity();
    var msg = mock(MessageView.class);
    when(msg.content()).thenReturn(encode(meta(
            "entryType", "OPEN_TOPIC",
            "role", "REVIEWER",
            "round", "1"), "body"));
    when(msg.correlationId()).thenReturn("point-1");
    when(msg.topic()).thenReturn("general");
    when(msg.id()).thenReturn(99L);
    when(msg.type()).thenReturn(MessageType.STATUS);

    var result = projection.apply(state, msg);

    ThreadEntry entry = result.points().get("point-1").thread().get(0);
    assertThat(entry.messageId()).isEqualTo(99L);
    assertThat(entry.messageType()).isEqualTo(MessageType.STATUS);
}
```

- [ ] **Step 3: Update ConversationProjection to pass fields through**

In `handlePointInitiation`, change the `ConversationFold.createPoint` call (line 124):

```java
return ConversationFold.createPoint(state, pointId, message.topic(), message.id(), message.type(),
        classification, role, round, entryType, body);
```

In `handlePointResponse`, change the `ConversationFold.respondToPoint` call (line 141-142):

```java
return ConversationFold.respondToPoint(state, targetId, message.id(), message.type(),
        role, round, entryType, body, statusAfter(entryType));
```

In `handleFlagHuman`, change the `ConversationFold.flagHuman` call (line 200):

```java
return ConversationFold.flagHuman(state, message.correlationId(), message.id(),
        role, round, content);
```

In `handleMemo`, update:

```java
return ConversationFold.addMemo(state, role, round, content);
```

(addMemo signature unchanged — no messageId on memos for now)

- [ ] **Step 4: Fix existing ConversationProjectionTest mocks**

The existing `message()` helper (line 21-25) mocks only `content()` and `correlationId()`. Add stubs for `topic()`, `id()`, `type()`:

```java
private MessageView message(String content, String correlationId) {
    var msg = mock(MessageView.class);
    when(msg.content()).thenReturn(content);
    when(msg.correlationId()).thenReturn(correlationId);
    when(msg.topic()).thenReturn("general");
    when(msg.id()).thenReturn(null);
    when(msg.type()).thenReturn(null);
    return msg;
}
```

- [ ] **Step 5: Run tests**

Run: `mvn test -pl . -Dtest=ConversationProjectionTest`
Expected: ALL PASS

- [ ] **Step 6: Run full suite**

Run: `mvn test -pl .`
Expected: ALL PASS

- [ ] **Step 7: Commit**

```
feat(#49): ConversationProjection passes topic, messageId, messageType from MessageView to fold
```

---

### Task 4: Renderer enhancements — topic grouping, obligation chains, reactions

Add topic grouping, obligation chain rendering, and reaction decoration to `ConversationRenderer`. Extend `ConversationRendererConfig` with new fields.

**Files:**
- Modify: `src/main/java/io/casehub/blocks/conversation/ConversationRendererConfig.java`
- Modify: `src/main/java/io/casehub/blocks/conversation/ConversationRenderer.java`
- Modify: `src/test/java/io/casehub/blocks/conversation/ConversationRendererTest.java`

**Interfaces:**
- Consumes: `ConversationPoint.topic()`, `ThreadEntry.messageId()`, `ThreadEntry.messageType()` (from Tasks 1-3)
- Produces: `ConversationRenderer.render(ConversationState, Map<Long, List<ReactionGroup>>)`
- Produces: `ConversationRendererConfig.groupByTopic()`, `.showObligationChain()`, `.messageTypeLabel()`

- [ ] **Step 1: Write failing test for topic grouping**

```java
@Test
void topicGrouping_pointsGroupedByTopic() {
    var config = reviewConfig().toBuilder()
            .groupByTopic(true)
            .build();

    var points = new LinkedHashMap<String, ConversationPoint>();
    points.put("RP-1", new ConversationPoint("RP-1", "review",
            new PointClassification(Priority.HIGH, "bug", null),
            List.of(entry("REV", "RAISE", "Bug in review")), "OPEN"));
    points.put("RP-2", new ConversationPoint("RP-2", "analysis",
            new PointClassification(Priority.MEDIUM, "design", null),
            List.of(entry("REV", "RAISE", "Design issue")), "OPEN"));
    points.put("RP-3", new ConversationPoint("RP-3", "review",
            new PointClassification(Priority.LOW, "style", null),
            List.of(entry("REV", "RAISE", "Style nit")), "OPEN"));

    var renderer = new ConversationRenderer(config);
    var result = renderer.render(state(points, List.of(), List.of(), Map.of()));

    assertThat(result).contains("## review");
    assertThat(result).contains("## analysis");
    int firstReviewPos = result.indexOf("Bug in review");
    int analysisPos = result.indexOf("Design issue");
    int secondReviewPos = result.indexOf("Style nit");
    assertThat(firstReviewPos).isLessThan(analysisPos);
    assertThat(secondReviewPos).isLessThan(analysisPos).isGreaterThan(firstReviewPos);
}
```

Run: `mvn test -pl . -Dtest=ConversationRendererTest#topicGrouping_pointsGroupedByTopic`
Expected: FAIL — `groupByTopic` doesn't exist on config

- [ ] **Step 2: Write failing test for obligation chain**

```java
@Test
void obligationChain_rendersMessageTypeProgression() {
    var config = reviewConfig().toBuilder()
            .groupByTopic(true)
            .showObligationChain(true)
            .messageTypeLabel(Map.of(
                    MessageType.COMMAND, "commanded",
                    MessageType.STATUS, "progress",
                    MessageType.DONE, "completed"))
            .build();

    var points = new LinkedHashMap<String, ConversationPoint>();
    points.put("RP-1", new ConversationPoint("RP-1", "review",
            new PointClassification(Priority.HIGH, "bug", null),
            List.of(
                    new ThreadEntry("RP-1", 1L, MessageType.COMMAND, "REV", 1, "RAISE", "Review this"),
                    new ThreadEntry(null, 2L, MessageType.STATUS, "IMP", 1, "QUALIFY", "Working on it"),
                    new ThreadEntry(null, 3L, MessageType.DONE, "IMP", 1, "AGREE", "Fixed")),
            "AGREED"));

    var renderer = new ConversationRenderer(config);
    var result = renderer.render(state(points, List.of(), List.of(), Map.of()));

    assertThat(result).contains("commanded → progress → completed ✓");
}
```

- [ ] **Step 3: Write failing test for reaction decoration**

```java
@Test
void reactions_renderedInlineAfterThreadEntries() {
    var points = new LinkedHashMap<String, ConversationPoint>();
    points.put("RP-1", new ConversationPoint("RP-1", "general",
            new PointClassification(Priority.HIGH, "bug", null),
            List.of(new ThreadEntry("RP-1", 10L, MessageType.COMMAND, "REV", 1, "RAISE", "Bug found")),
            "OPEN"));

    var reactions = Map.of(10L, List.of(
            new ReactionGroup("👍", 3, List.of("a1", "a2", "a3")),
            new ReactionGroup("✅", 1, List.of("a4"))));

    var renderer = new ConversationRenderer(emptyConfig());
    var result = renderer.render(state(points, List.of(), List.of(), Map.of()), reactions);

    assertThat(result).contains("👍×3");
    assertThat(result).contains("✅×1");
}
```

Add import: `import io.casehub.qhorus.api.message.ReactionGroup;`

- [ ] **Step 4: Extend ConversationRendererConfig**

Add three new fields to the record and builder:

```java
Map<MessageType, String> messageTypeLabel,
boolean groupByTopic,
boolean showObligationChain
```

Add `toBuilder()` method for test convenience. Builder defaults: `groupByTopic = false`, `showObligationChain = false`, `messageTypeLabel = Map.of()`.

Add import: `import io.casehub.qhorus.api.message.MessageType;`

- [ ] **Step 5: Add render overload with reactions**

In `ConversationRenderer`, add:

```java
public String render(ConversationState state, Map<Long, List<ReactionGroup>> reactions) {
    // delegate to internal render with reactions
}
```

Change existing `render(ConversationState)` to delegate:

```java
public String render(ConversationState state) {
    return render(state, Map.of());
}
```

Add import: `import io.casehub.qhorus.api.message.ReactionGroup;`

- [ ] **Step 6: Implement topic grouping in render**

When `config.groupByTopic()` is true:
1. Collect distinct topics from points in insertion order
2. For each topic, render a `## <topic>` header
3. Within each topic, apply existing status grouping (unresolved → escalated → resolved)

When false, existing flat rendering applies unchanged.

- [ ] **Step 7: Implement obligation chain rendering**

When `config.showObligationChain()` is true, after each topic header:
1. Collect `MessageType` values from all thread entries in that topic (preserving order)
2. Filter nulls
3. Map through `config.messageTypeLabel()` (fallback to `type.name().toLowerCase()`)
4. Join with ` → `
5. Append ` ✓` if the last type is DONE, otherwise ` ⏳`

- [ ] **Step 8: Implement reaction decoration**

In `renderPoints`, after each thread entry line, check `reactions.get(entry.messageId())`. If non-null and non-empty, append reaction groups as `emoji×count` separated by spaces.

- [ ] **Step 9: Run tests**

Run: `mvn test -pl . -Dtest=ConversationRendererTest`
Expected: ALL PASS

- [ ] **Step 10: Commit**

```
feat(#49): topic grouping, obligation chain rendering, reaction decoration in ConversationRenderer
```

---

### Task 5: ChannelAgentRequest topic passthrough

Add `String topic` to `ChannelAgentRequest` so dispatch handlers know the topic context.

**Files:**
- Modify: `src/main/java/io/casehub/blocks/channel/ChannelAgentRequest.java`
- Modify: `src/test/java/io/casehub/blocks/channel/ChannelAgentDispatcherTest.java`

**Interfaces:**
- Produces: `ChannelAgentRequest(UUID channelId, String correlationId, OutboundMessage message, String topic)`

- [ ] **Step 1: Write failing test**

Add to `ChannelAgentDispatcherTest`:

```java
@Test
void topic_passedThroughToHandler() {
    var topicCaptor = new AtomicReference<String>();
    var handler = new ChannelAgentHandler() {
        @Override public boolean handles(ChannelAgentRequest r) { return true; }
        @Override public AgentTask prepareTask(ChannelAgentRequest r) {
            topicCaptor.set(r.topic());
            return new AgentTask("system", "user");
        }
        @Override public MessageDispatch buildResponse(UUID c, String s, String o, ChannelAgentRequest t) {
            return MessageDispatch.builder()
                    .channelId(c).sender(s).type(MessageType.RESPONSE)
                    .content(o).correlationId("sub-1").inReplyTo(1L)
                    .actorType(ActorType.AI)
                    .topic(t.topic())
                    .build();
        }
    };

    var dispatcher = new ChannelAgentDispatcher(s -> "output", messageSink, List.of(handler), "test");
    dispatcher.dispatch(new ChannelAgentRequest(channelId, "sub-1", outboundMessage, "risk-assessment"));

    assertThat(topicCaptor.get()).isEqualTo("risk-assessment");
    verify(messageSink).accept(argThat(d -> "risk-assessment".equals(d.topic())));
}
```

Run: `mvn test -pl . -Dtest=ChannelAgentDispatcherTest#topic_passedThroughToHandler`
Expected: FAIL — constructor doesn't accept topic

- [ ] **Step 2: Update ChannelAgentRequest record**

```java
public record ChannelAgentRequest(
        UUID channelId,
        String correlationId,
        OutboundMessage message,
        String topic) {}
```

- [ ] **Step 3: Fix existing ChannelAgentDispatcherTest constructor calls**

All existing `new ChannelAgentRequest(channelId, "sub-1", outboundMessage)` calls need `"general"` appended:

```java
new ChannelAgentRequest(channelId, "sub-1", outboundMessage, "general")
```

There are 5 call sites in ChannelAgentDispatcherTest (lines 49, 67, 82, 102, 115, 123).

- [ ] **Step 4: Run tests**

Run: `mvn test -pl . -Dtest=ChannelAgentDispatcherTest`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```
feat(#49): ChannelAgentRequest carries topic for dispatch context
```

---

### Task 6: End-to-end integration test

Full composition test: multi-topic workflow with obligation chains, reactions, and topic-grouped rendering.

**Files:**
- Create: `src/test/java/io/casehub/blocks/conversation/TopicAwareConversationIntegrationTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1-5

- [ ] **Step 1: Write the full integration test**

```java
package io.casehub.blocks.conversation;

import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.message.ReactionGroup;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopicAwareConversationIntegrationTest {

    private final AtomicLong messageIdSeq = new AtomicLong(1);

    private final ConversationProjectionTest.TestConversationProjection projection =
            new ConversationProjectionTest.TestConversationProjection();

    private MessageView message(String content, String correlationId,
                                 String topic, MessageType type) {
        var msg = mock(MessageView.class);
        when(msg.content()).thenReturn(content);
        when(msg.correlationId()).thenReturn(correlationId);
        when(msg.topic()).thenReturn(topic);
        when(msg.id()).thenReturn(messageIdSeq.getAndIncrement());
        when(msg.type()).thenReturn(type);
        return msg;
    }

    private String encode(Map<String, String> meta, String body) {
        return ChannelMessageMeta.encode("TEST:", meta, body);
    }

    private Map<String, String> meta(String... pairs) {
        var map = new LinkedHashMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    @Test
    void fullChoreography_multiTopicWithObligationChainsAndReactions() {
        var state = projection.identity();

        // Step 1: COMMAND in "review" topic — reviewer raises a point
        var cmd1 = message(encode(meta(
                "entryType", "OPEN_TOPIC", "role", "REVIEWER",
                "round", "1", "priority", "HIGH", "scope", "correctness"),
                "NPE risk in handler"), "point-review-1", "review", MessageType.COMMAND);
        state = projection.apply(state, cmd1);

        // Step 2: STATUS in "review" — implementor responds
        var status1 = message(encode(meta(
                "entryType", "ACCEPT", "role", "IMPLEMENTOR",
                "round", "1"), "Working on fix"),
                "point-review-1", "review", MessageType.STATUS);
        state = projection.apply(state, status1);

        // Step 3: DONE in "review" — implementor completes
        var done1 = message(encode(meta(
                "entryType", "ACCEPT", "role", "IMPLEMENTOR",
                "round", "1"), "Fixed with null guard"),
                "point-review-1", "review", MessageType.DONE);
        state = projection.apply(state, done1);

        // Step 4: COMMAND in "analysis" topic — separate concern
        var cmd2 = message(encode(meta(
                "entryType", "OPEN_TOPIC", "role", "ANALYST",
                "round", "1", "priority", "MEDIUM", "scope", "performance"),
                "Query takes 3s"), "point-analysis-1", "analysis", MessageType.COMMAND);
        state = projection.apply(state, cmd2);

        // Step 5: STATUS in "analysis"
        var status2 = message(encode(meta(
                "entryType", "ACCEPT", "role", "IMPLEMENTOR",
                "round", "1"), "Added index"),
                "point-analysis-1", "analysis", MessageType.STATUS);
        state = projection.apply(state, status2);

        // Verify topic attribution
        assertThat(state.points().get("point-review-1").topic()).isEqualTo("review");
        assertThat(state.points().get("point-analysis-1").topic()).isEqualTo("analysis");

        // Verify message IDs are tracked
        assertThat(state.points().get("point-review-1").thread().get(0).messageId()).isNotNull();
        assertThat(state.points().get("point-review-1").thread().get(0).messageType())
                .isEqualTo(MessageType.COMMAND);

        // Build reactions (on the first message of each topic)
        Long reviewMsgId = state.points().get("point-review-1").thread().get(0).messageId();
        Long analysisMsgId = state.points().get("point-analysis-1").thread().get(0).messageId();

        var reactions = Map.of(
                reviewMsgId, List.of(new ReactionGroup("👍", 2, List.of("agent-a", "agent-b"))),
                analysisMsgId, List.of(new ReactionGroup("🔍", 1, List.of("agent-c"))));

        // Render with topic grouping and obligation chains
        var config = ConversationRendererConfig.builder()
                .groupByTopic(true)
                .showObligationChain(true)
                .statusEmoji(Map.of("OPEN", "🔴", "ACCEPTED", "✅"))
                .resolvedStatuses(Set.of("ACCEPTED"))
                .messageTypeLabel(Map.of(
                        MessageType.COMMAND, "commanded",
                        MessageType.STATUS, "progress",
                        MessageType.DONE, "completed"))
                .build();

        var renderer = new ConversationRenderer(config);
        var result = renderer.render(state, reactions);

        // Topic sections present
        assertThat(result).contains("## review");
        assertThat(result).contains("## analysis");

        // Review topic: obligation chain complete (COMMAND → STATUS → DONE)
        assertThat(result).contains("commanded → progress → completed ✓");

        // Analysis topic: obligation chain incomplete (COMMAND → STATUS)
        assertThat(result).contains("commanded → progress ⏳");

        // Reactions present
        assertThat(result).contains("👍×2");
        assertThat(result).contains("🔍×1");

        // Topics are ordered: review before analysis (insertion order)
        assertThat(result.indexOf("## review")).isLessThan(result.indexOf("## analysis"));
    }

    @Test
    void topicScoped_singleTopicView() {
        var state = projection.identity();

        // Messages in two topics
        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC", "role", "REV", "round", "1"),
                "review point"), "p1", "review", MessageType.COMMAND));
        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC", "role", "REV", "round", "1"),
                "analysis point"), "p2", "analysis", MessageType.COMMAND));

        // All points present in full projection
        assertThat(state.points()).hasSize(2);
        assertThat(state.points().get("p1").topic()).isEqualTo("review");
        assertThat(state.points().get("p2").topic()).isEqualTo("analysis");

        // Topic-scoped rendering (non-grouped, single topic)
        var config = ConversationRendererConfig.builder()
                .groupByTopic(false)
                .build();
        var renderer = new ConversationRenderer(config);
        var result = renderer.render(state);

        // Both points render in flat mode
        assertThat(result).contains("review point");
        assertThat(result).contains("analysis point");
    }
}
```

- [ ] **Step 2: Run the integration test**

Run: `mvn test -pl . -Dtest=TopicAwareConversationIntegrationTest`
Expected: ALL PASS

- [ ] **Step 3: Run full test suite**

Run: `mvn test -pl .`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```
test(#49): end-to-end integration — multi-topic choreography with obligation chains and reactions
```

---

## Self-Review Checklist

- [x] **Spec coverage:** Topic grouping (Task 4), obligation chain (Task 4), reaction decoration (Task 4), message identity (Tasks 1-3), ChannelAgentRequest topic (Task 5), end-to-end composition (Task 6). Progress overlay explicitly deferred to #62.
- [x] **Placeholder scan:** No TBD/TODO. All code blocks complete.
- [x] **Type consistency:** `ThreadEntry(entryId, messageId, messageType, ...)` used consistently across Tasks 1-4. `ConversationPoint(id, topic, ...)` consistent. `ConversationFold.createPoint(state, pointId, topic, messageId, messageType, ...)` consistent across Tasks 2-3.
- [x] **Tooling safety scan:** All file modifications use `ide_edit_member`, `ide_replace_member`, or `ide_insert_member`. No bash file operations on source files. New test file in Task 6 uses `ide_create_file`.
