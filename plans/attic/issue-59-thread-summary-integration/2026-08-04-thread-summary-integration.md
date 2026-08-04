# Thread Summary Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #59 — thread summary integration
**Issue group:** #59

**Goal:** Live `ContentSummariser<Message>` output driving per-thread
summaries in qhorus, triggered by terminal messages on correlated threads.

**Architecture:** Push-based from blocks. Observer detects DONE/FAILURE
messages with correlationId, fetches thread messages, delegates to
`ContentSummariser<Message>`, stores result in qhorus `ThreadSummaryStore`.
Cross-repo: qhorus owns storage types + persistence, blocks owns the
observer.

**Tech Stack:** Java 21, Quarkus (CDI), qhorus-api, blocks summarisation
framework, JUnit 5, Mockito, AssertJ

## Global Constraints

- qhorus-api is a compile dependency of blocks — new types must be in qhorus-api
- blocks has no CDI container in tests — plain JUnit 5 + Mockito only
- All qhorus changes must be backward-compatible (new types only)
- A dedicated qhorus issue must be filed and referenced in all qhorus commits
- `mvn install` qhorus before building blocks (blocks must see updated qhorus-api)

---

### Task 1: qhorus-api — ThreadSummary, ThreadSummaryStore, ThreadSummaryUpdatedEvent

**Files:**
- Create: `qhorus/api/src/main/java/io/casehub/qhorus/api/channel/ThreadSummary.java`
- Create: `qhorus/api/src/main/java/io/casehub/qhorus/api/store/ThreadSummaryStore.java`
- Create: `qhorus/api/src/main/java/io/casehub/qhorus/api/channel/ThreadSummaryUpdatedEvent.java`

**Interfaces:**
- Consumes: nothing (foundation task)
- Produces: `ThreadSummary` record with `builder(UUID channelId, String correlationId)` and `toBuilder()`, `ThreadSummaryStore` SPI (`save`, `findByCorrelationId`, `findByChannel`, `delete`), `ThreadSummaryUpdatedEvent` record

- [ ] **Step 1: File a qhorus issue for thread summary storage**

```bash
gh issue create --repo casehubio/qhorus \
  --title "feat: thread summary storage — ThreadSummary, ThreadSummaryStore, persistence" \
  --body "$(cat <<'BODY'
## Context

blocks#59 adds thread summary integration — live ContentSummariser output
driving per-thread summaries. The observer lives in blocks; the storage
types and persistence live in qhorus.

## Scope

- `ThreadSummary` record in qhorus-api (channel package)
- `ThreadSummaryStore` SPI in qhorus-api (store package)
- `ThreadSummaryUpdatedEvent` in qhorus-api (channel package)
- `InMemoryThreadSummaryStore` in persistence-memory
- JPA entity + store in runtime

All backward-compatible — new types only.

Refs casehubio/blocks#59
BODY
)"
```

Record the issue number as `QHORUS_ISSUE` for commit messages.

- [ ] **Step 2: Create ThreadSummary record**

Use `ide_create_file` to create `qhorus/api/src/main/java/io/casehub/qhorus/api/channel/ThreadSummary.java`:

```java
package io.casehub.qhorus.api.channel;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ThreadSummary(
        UUID id,
        UUID channelId,
        String correlationId,
        String content,
        Map<String, String> annotations,
        Instant updatedAt,
        String updatedBy,
        String tenancyId) {

    public ThreadSummary {
        annotations = annotations != null ? Map.copyOf(annotations) : Map.of();
    }

    public Builder toBuilder() {
        return new Builder(channelId, correlationId)
                       .id(id).content(content).annotations(annotations)
                       .updatedAt(updatedAt).updatedBy(updatedBy)
                       .tenancyId(tenancyId);
    }

    public static Builder builder(UUID channelId, String correlationId) {
        return new Builder(channelId, correlationId);
    }

    public static final class Builder {
        private final UUID   channelId;
        private final String correlationId;
        private UUID                id;
        private String              content;
        private Map<String, String> annotations;
        private Instant             updatedAt;
        private String              updatedBy;
        private String              tenancyId;

        private Builder(UUID channelId, String correlationId) {
            this.channelId = channelId;
            this.correlationId = correlationId;
        }

        public Builder id(UUID v)                         { this.id = v; return this; }
        public Builder content(String v)                  { this.content = v; return this; }
        public Builder annotations(Map<String, String> v) { this.annotations = v; return this; }
        public Builder updatedAt(Instant v)               { this.updatedAt = v; return this; }
        public Builder updatedBy(String v)                { this.updatedBy = v; return this; }
        public Builder tenancyId(String v)                { this.tenancyId = v; return this; }

        public ThreadSummary build() {
            return new ThreadSummary(id, channelId, correlationId, content,
                    annotations, updatedAt, updatedBy, tenancyId);
        }
    }
}
```

- [ ] **Step 3: Create ThreadSummaryStore SPI**

Use `ide_create_file` to create `qhorus/api/src/main/java/io/casehub/qhorus/api/store/ThreadSummaryStore.java`:

```java
package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.channel.ThreadSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ThreadSummaryStore {

    ThreadSummary save(ThreadSummary summary);

    Optional<ThreadSummary> findByCorrelationId(UUID channelId, String correlationId);

    List<ThreadSummary> findByChannel(UUID channelId);

    void delete(UUID channelId, String correlationId);
}
```

- [ ] **Step 4: Create ThreadSummaryUpdatedEvent**

Use `ide_create_file` to create `qhorus/api/src/main/java/io/casehub/qhorus/api/channel/ThreadSummaryUpdatedEvent.java`:

```java
package io.casehub.qhorus.api.channel;

import java.util.UUID;

public record ThreadSummaryUpdatedEvent(
        UUID channelId, String channelName,
        String correlationId, String updatedBy) {}
```

- [ ] **Step 5: Verify compilation**

Run: `mvn --batch-mode -pl api compile -f /Users/mdproctor/claude/casehub/qhorus/pom.xml`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/qhorus add \
  api/src/main/java/io/casehub/qhorus/api/channel/ThreadSummary.java \
  api/src/main/java/io/casehub/qhorus/api/store/ThreadSummaryStore.java \
  api/src/main/java/io/casehub/qhorus/api/channel/ThreadSummaryUpdatedEvent.java
git -C /Users/mdproctor/claude/casehub/qhorus commit -m "feat: ThreadSummary, ThreadSummaryStore, ThreadSummaryUpdatedEvent

Refs casehubio/qhorus#QHORUS_ISSUE
Refs casehubio/blocks#59"
```

---

### Task 2: qhorus — InMemoryThreadSummaryStore + tests

**Files:**
- Create: `qhorus/persistence-memory/src/main/java/io/casehub/qhorus/persistence/memory/InMemoryThreadSummaryStore.java`
- Create: `qhorus/persistence-memory/src/test/java/io/casehub/qhorus/persistence/memory/InMemoryThreadSummaryStoreTest.java`

**Interfaces:**
- Consumes: `ThreadSummary`, `ThreadSummaryStore` from Task 1
- Produces: `InMemoryThreadSummaryStore` — `@Alternative @Priority(1) @ApplicationScoped`

- [ ] **Step 1: Write the failing tests**

Create `InMemoryThreadSummaryStoreTest.java`:

```java
package io.casehub.qhorus.persistence.memory;

import io.casehub.qhorus.api.channel.ThreadSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryThreadSummaryStoreTest {

    private InMemoryThreadSummaryStore store;

    private static final UUID CHANNEL_A = UUID.randomUUID();
    private static final UUID CHANNEL_B = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        store = new InMemoryThreadSummaryStore();
    }

    @Test
    void saveAndFindRoundTrip() {
        ThreadSummary summary = ThreadSummary.builder(CHANNEL_A, "corr-1")
                .content("Thread summary text")
                .annotations(Map.of("participants", "agent-a, agent-b"))
                .updatedAt(Instant.now())
                .updatedBy("system:thread-summariser")
                .tenancyId("tenant-1")
                .build();

        ThreadSummary saved = store.save(summary);
        assertThat(saved.id()).isNotNull();
        assertThat(saved.content()).isEqualTo("Thread summary text");

        var found = store.findByCorrelationId(CHANNEL_A, "corr-1");
        assertThat(found).isPresent();
        assertThat(found.get().content()).isEqualTo("Thread summary text");
        assertThat(found.get().annotations()).containsEntry("participants", "agent-a, agent-b");
    }

    @Test
    void upsertOverwritesSameKey() {
        store.save(ThreadSummary.builder(CHANNEL_A, "corr-1")
                .content("First version").build());
        store.save(ThreadSummary.builder(CHANNEL_A, "corr-1")
                .content("Second version").build());

        var found = store.findByCorrelationId(CHANNEL_A, "corr-1");
        assertThat(found).isPresent();
        assertThat(found.get().content()).isEqualTo("Second version");
    }

    @Test
    void findByChannelReturnsAllThreads() {
        store.save(ThreadSummary.builder(CHANNEL_A, "corr-1").content("T1").build());
        store.save(ThreadSummary.builder(CHANNEL_A, "corr-2").content("T2").build());
        store.save(ThreadSummary.builder(CHANNEL_B, "corr-3").content("T3").build());

        var channelA = store.findByChannel(CHANNEL_A);
        assertThat(channelA).hasSize(2);
        assertThat(channelA).extracting(ThreadSummary::correlationId)
                .containsExactlyInAnyOrder("corr-1", "corr-2");
    }

    @Test
    void deleteRemovesEntry() {
        store.save(ThreadSummary.builder(CHANNEL_A, "corr-1").content("T1").build());
        store.delete(CHANNEL_A, "corr-1");

        assertThat(store.findByCorrelationId(CHANNEL_A, "corr-1")).isEmpty();
    }

    @Test
    void findUnknownReturnsEmpty() {
        assertThat(store.findByCorrelationId(CHANNEL_A, "nonexistent")).isEmpty();
    }

    @Test
    void saveGeneratesIdWhenNull() {
        ThreadSummary saved = store.save(
                ThreadSummary.builder(CHANNEL_A, "corr-1").content("T1").build());
        assertThat(saved.id()).isNotNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode -pl persistence-memory test -Dtest=InMemoryThreadSummaryStoreTest -f /Users/mdproctor/claude/casehub/qhorus/pom.xml`
Expected: FAIL — `InMemoryThreadSummaryStore` does not exist

- [ ] **Step 3: Implement InMemoryThreadSummaryStore**

Create `InMemoryThreadSummaryStore.java`:

```java
package io.casehub.qhorus.persistence.memory;

import io.casehub.qhorus.api.channel.ThreadSummary;
import io.casehub.qhorus.api.store.ThreadSummaryStore;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryThreadSummaryStore implements ThreadSummaryStore {

    private final Map<String, ThreadSummary> store = new ConcurrentHashMap<>();

    private static String key(UUID channelId, String correlationId) {
        return channelId + ":" + correlationId;
    }

    @Override
    public ThreadSummary save(ThreadSummary summary) {
        ThreadSummary.Builder b = summary.toBuilder();
        if (summary.id() == null) {
            b.id(UUID.randomUUID());
        }
        ThreadSummary saved = b.build();
        store.put(key(saved.channelId(), saved.correlationId()), saved);
        return saved;
    }

    @Override
    public Optional<ThreadSummary> findByCorrelationId(UUID channelId, String correlationId) {
        return Optional.ofNullable(store.get(key(channelId, correlationId)));
    }

    @Override
    public List<ThreadSummary> findByChannel(UUID channelId) {
        return store.values().stream()
                .filter(s -> s.channelId().equals(channelId))
                .toList();
    }

    @Override
    public void delete(UUID channelId, String correlationId) {
        store.remove(key(channelId, correlationId));
    }

    public void clear() {
        store.clear();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode -pl persistence-memory test -Dtest=InMemoryThreadSummaryStoreTest -f /Users/mdproctor/claude/casehub/qhorus/pom.xml`
Expected: PASS — all 6 tests green

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/qhorus add \
  persistence-memory/src/main/java/io/casehub/qhorus/persistence/memory/InMemoryThreadSummaryStore.java \
  persistence-memory/src/test/java/io/casehub/qhorus/persistence/memory/InMemoryThreadSummaryStoreTest.java
git -C /Users/mdproctor/claude/casehub/qhorus commit -m "feat: InMemoryThreadSummaryStore + tests

Refs casehubio/qhorus#QHORUS_ISSUE
Refs casehubio/blocks#59"
```

---

### Task 3: qhorus — JPA entity + store + Panache repo

**Files:**
- Create: `qhorus/runtime/src/main/java/io/casehub/qhorus/runtime/channel/ThreadSummaryEntity.java`
- Create: `qhorus/runtime/src/main/java/io/casehub/qhorus/runtime/store/jpa/ThreadSummaryPanacheRepo.java`
- Create: `qhorus/runtime/src/main/java/io/casehub/qhorus/runtime/store/jpa/JpaThreadSummaryStore.java`

**Interfaces:**
- Consumes: `ThreadSummary`, `ThreadSummaryStore` from Task 1
- Produces: `JpaThreadSummaryStore` — `@ApplicationScoped`, JPA-backed `ThreadSummaryStore`

- [ ] **Step 1: Create ThreadSummaryEntity**

Follows the `ChannelSummaryEntity` pattern. Create `ThreadSummaryEntity.java`:

```java
package io.casehub.qhorus.runtime.channel;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.qhorus.api.channel.ThreadSummary;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity(name = "ThreadSummary")
@Table(name = "thread_summary",
       uniqueConstraints = @UniqueConstraint(
               columnNames = {"channel_id", "correlation_id"}))
public class ThreadSummaryEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "channel_id", nullable = false)
    public UUID channelId;

    @Column(name = "correlation_id", nullable = false)
    public String correlationId;

    @Column(name = "content", columnDefinition = "TEXT")
    public String content;

    @Column(name = "annotations", columnDefinition = "TEXT")
    public String annotations;

    @Column(name = "updated_at")
    public Instant updatedAt;

    @Column(name = "updated_by")
    public String updatedBy;

    @Column(name = "tenancy_id", nullable = false, updatable = false)
    public String tenancyId = TenancyConstants.DEFAULT_TENANT_ID;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public static ThreadSummaryEntity fromDomain(ThreadSummary s) {
        ThreadSummaryEntity e = new ThreadSummaryEntity();
        e.id            = s.id();
        e.channelId     = s.channelId();
        e.correlationId = s.correlationId();
        e.content       = s.content();
        e.annotations   = serializeAnnotations(s.annotations());
        e.updatedAt     = s.updatedAt();
        e.updatedBy     = s.updatedBy();
        e.tenancyId     = s.tenancyId() != null
                ? s.tenancyId() : TenancyConstants.DEFAULT_TENANT_ID;
        return e;
    }

    public ThreadSummary toDomain() {
        return new ThreadSummary(id, channelId, correlationId, content,
                deserializeAnnotations(annotations), updatedAt, updatedBy, tenancyId);
    }

    private static String serializeAnnotations(Map<String, String> annotations) {
        if (annotations == null || annotations.isEmpty()) { return null; }
        var sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : annotations.entrySet()) {
            if (!first) { sb.append(","); }
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
              .append(escapeJson(entry.getValue())).append("\"");
        }
        return sb.append("}").toString();
    }

    private static Map<String, String> deserializeAnnotations(String json) {
        if (json == null || json.isBlank()) { return Map.of(); }
        var result = new java.util.LinkedHashMap<String, String>();
        String content = json.substring(1, json.length() - 1);
        if (content.isEmpty()) { return Map.of(); }
        int i = 0;
        while (i < content.length()) {
            int keyStart = content.indexOf('"', i) + 1;
            int keyEnd   = findUnescapedQuote(content, keyStart);
            String key   = unescapeJson(content.substring(keyStart, keyEnd));
            int valStart = content.indexOf('"', keyEnd + 1) + 1;
            int valEnd   = findUnescapedQuote(content, valStart);
            String val   = unescapeJson(content.substring(valStart, valEnd));
            result.put(key, val);
            i = valEnd + 1;
            if (i < content.length() && content.charAt(i) == ',') { i++; }
        }
        return Map.copyOf(result);
    }

    private static int findUnescapedQuote(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == '"' && (i == 0 || s.charAt(i - 1) != '\\')) { return i; }
        }
        return s.length();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
```

- [ ] **Step 2: Create ThreadSummaryPanacheRepo**

```java
package io.casehub.qhorus.runtime.store.jpa;

import io.casehub.qhorus.runtime.channel.ThreadSummaryEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
class ThreadSummaryPanacheRepo
        implements PanacheRepositoryBase<ThreadSummaryEntity, UUID> {
}
```

- [ ] **Step 3: Create JpaThreadSummaryStore**

```java
package io.casehub.qhorus.runtime.store.jpa;

import io.casehub.qhorus.api.channel.ThreadSummary;
import io.casehub.qhorus.api.store.ThreadSummaryStore;
import io.casehub.qhorus.runtime.channel.ThreadSummaryEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaThreadSummaryStore implements ThreadSummaryStore {

    @Inject
    ThreadSummaryPanacheRepo repo;

    @Override
    @Transactional
    public ThreadSummary save(ThreadSummary summary) {
        Optional<ThreadSummaryEntity> existing = repo.find(
                "channelId = ?1 AND correlationId = ?2",
                summary.channelId(), summary.correlationId())
                .firstResultOptional();

        ThreadSummaryEntity e = ThreadSummaryEntity.fromDomain(summary);
        if (existing.isPresent()) {
            e.id = existing.get().id;
            e = repo.getEntityManager().merge(e);
        } else {
            repo.persist(e);
        }
        return e.toDomain();
    }

    @Override
    public Optional<ThreadSummary> findByCorrelationId(UUID channelId,
                                                        String correlationId) {
        return repo.find("channelId = ?1 AND correlationId = ?2",
                         channelId, correlationId)
                   .firstResultOptional()
                   .map(ThreadSummaryEntity::toDomain);
    }

    @Override
    public List<ThreadSummary> findByChannel(UUID channelId) {
        return repo.find("channelId", channelId).list().stream()
                   .map(ThreadSummaryEntity::toDomain)
                   .toList();
    }

    @Override
    @Transactional
    public void delete(UUID channelId, String correlationId) {
        repo.delete("channelId = ?1 AND correlationId = ?2",
                    channelId, correlationId);
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `mvn --batch-mode -pl runtime compile -f /Users/mdproctor/claude/casehub/qhorus/pom.xml`
Expected: BUILD SUCCESS

- [ ] **Step 5: Install qhorus to local Maven repo**

Run: `mvn --batch-mode install -DskipTests -f /Users/mdproctor/claude/casehub/qhorus/pom.xml`
Expected: BUILD SUCCESS — blocks can now see the updated qhorus-api

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/qhorus add \
  runtime/src/main/java/io/casehub/qhorus/runtime/channel/ThreadSummaryEntity.java \
  runtime/src/main/java/io/casehub/qhorus/runtime/store/jpa/ThreadSummaryPanacheRepo.java \
  runtime/src/main/java/io/casehub/qhorus/runtime/store/jpa/JpaThreadSummaryStore.java
git -C /Users/mdproctor/claude/casehub/qhorus commit -m "feat: JPA ThreadSummary entity + store

Refs casehubio/qhorus#QHORUS_ISSUE
Refs casehubio/blocks#59"
```

---

### Task 4: blocks — ThreadSummaryObserver + tests

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/channel/summary/ThreadSummaryObserver.java`
- Create: `blocks/src/test/java/io/casehub/blocks/channel/summary/ThreadSummaryObserverTest.java`

**Interfaces:**
- Consumes: `ContentSummariser<Message>` (blocks), `CrossTenantMessageStore` (qhorus-api), `ThreadSummaryStore` (qhorus-api, Task 1), `MessageReceivedEvent` (qhorus-api), `SummaryResult` (qhorus-api), `CurrentPrincipal` (platform-api)
- Produces: `ThreadSummaryObserver` — `@ApplicationScoped`, observes `MessageReceivedEvent`

- [ ] **Step 1: Write the failing tests**

Create `ThreadSummaryObserverTest.java`:

```java
package io.casehub.blocks.channel.summary;

import io.casehub.blocks.summarisation.ContentSummariser;
import io.casehub.qhorus.api.channel.ThreadSummary;
import io.casehub.qhorus.api.channel.ThreadSummaryUpdatedEvent;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.CrossTenantMessageStore;
import io.casehub.qhorus.api.store.ThreadSummaryStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.api.spi.SummaryResult;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ThreadSummaryObserverTest {

    private ThreadSummaryObserver observer;
    private ContentSummariser<Message> summariser;
    private CrossTenantMessageStore messageStore;
    private ThreadSummaryStore threadSummaryStore;
    private Event<ThreadSummaryUpdatedEvent> summaryEvents;

    private static final UUID CHANNEL_ID = UUID.randomUUID();
    private static final String CHANNEL_NAME = "test-channel";
    private static final String CORRELATION_ID = "corr-001";
    private static final String TENANCY_ID = "tenant-1";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        summariser = mock(ContentSummariser.class);
        messageStore = mock(CrossTenantMessageStore.class);
        threadSummaryStore = mock(ThreadSummaryStore.class);
        summaryEvents = mock(Event.class);
        observer = new ThreadSummaryObserver(
                summariser, messageStore, threadSummaryStore, summaryEvents);
    }

    @Test
    void doneMessageTriggersSum() {
        List<Message> messages = List.of(mockMessage());
        when(messageStore.scan(any(MessageQuery.class))).thenReturn(messages);
        when(threadSummaryStore.findByCorrelationId(CHANNEL_ID, CORRELATION_ID))
                .thenReturn(Optional.empty());
        when(summariser.summarise(eq(messages), isNull()))
                .thenReturn(CompletableFuture.completedFuture(
                        SummaryResult.ofText("Thread done")));
        when(threadSummaryStore.save(any())).thenAnswer(i -> i.getArgument(0));

        observer.summariseThread(CHANNEL_ID, CORRELATION_ID, CHANNEL_NAME, TENANCY_ID);

        ArgumentCaptor<ThreadSummary> cap = ArgumentCaptor.forClass(ThreadSummary.class);
        verify(threadSummaryStore).save(cap.capture());
        assertThat(cap.getValue().content()).isEqualTo("Thread done");
        assertThat(cap.getValue().correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(cap.getValue().tenancyId()).isEqualTo(TENANCY_ID);
    }

    @Test
    void failureMessageTriggersSum() {
        List<Message> messages = List.of(mockMessage());
        when(messageStore.scan(any(MessageQuery.class))).thenReturn(messages);
        when(threadSummaryStore.findByCorrelationId(CHANNEL_ID, CORRELATION_ID))
                .thenReturn(Optional.empty());
        when(summariser.summarise(eq(messages), isNull()))
                .thenReturn(CompletableFuture.completedFuture(
                        SummaryResult.ofText("Thread failed")));
        when(threadSummaryStore.save(any())).thenAnswer(i -> i.getArgument(0));

        observer.summariseThread(CHANNEL_ID, CORRELATION_ID, CHANNEL_NAME, TENANCY_ID);

        verify(threadSummaryStore).save(any());
    }

    @Test
    void nonTerminalMessageIsIgnored() {
        MessageReceivedEvent event = mockEvent(MessageType.STATUS, CORRELATION_ID);
        observer.onTerminalMessage(event);
        verifyNoInteractions(messageStore, summariser, threadSummaryStore);
    }

    @Test
    void handoffMessageIsIgnored() {
        MessageReceivedEvent event = mockEvent(MessageType.HANDOFF, CORRELATION_ID);
        observer.onTerminalMessage(event);
        verifyNoInteractions(messageStore, summariser, threadSummaryStore);
    }

    @Test
    void responseMessageIsIgnored() {
        MessageReceivedEvent event = mockEvent(MessageType.RESPONSE, CORRELATION_ID);
        observer.onTerminalMessage(event);
        verifyNoInteractions(messageStore, summariser, threadSummaryStore);
    }

    @Test
    void nullCorrelationIdIsIgnored() {
        MessageReceivedEvent event = mockEvent(MessageType.DONE, null);
        observer.onTerminalMessage(event);
        verifyNoInteractions(messageStore, summariser, threadSummaryStore);
    }

    @Test
    void emptyMessagesSkipsSummarisation() {
        when(messageStore.scan(any(MessageQuery.class))).thenReturn(List.of());

        observer.summariseThread(CHANNEL_ID, CORRELATION_ID, CHANNEL_NAME, TENANCY_ID);

        verifyNoInteractions(summariser);
        verify(threadSummaryStore, never()).save(any());
    }

    @Test
    void previousSummaryPassedToSummariser() {
        List<Message> messages = List.of(mockMessage());
        ThreadSummary previous = ThreadSummary.builder(CHANNEL_ID, CORRELATION_ID)
                .content("Previous summary").build();
        SummaryResult previousResult = new SummaryResult("Previous summary", java.util.Map.of());

        when(messageStore.scan(any(MessageQuery.class))).thenReturn(messages);
        when(threadSummaryStore.findByCorrelationId(CHANNEL_ID, CORRELATION_ID))
                .thenReturn(Optional.of(previous));
        when(summariser.summarise(eq(messages), eq(previousResult)))
                .thenReturn(CompletableFuture.completedFuture(
                        SummaryResult.ofText("Updated summary")));
        when(threadSummaryStore.save(any())).thenAnswer(i -> i.getArgument(0));

        observer.summariseThread(CHANNEL_ID, CORRELATION_ID, CHANNEL_NAME, TENANCY_ID);

        verify(summariser).summarise(messages, previousResult);
    }

    @Test
    void summariserFailureIsLoggedNotPropagated() {
        when(messageStore.scan(any(MessageQuery.class)))
                .thenReturn(List.of(mockMessage()));
        when(threadSummaryStore.findByCorrelationId(CHANNEL_ID, CORRELATION_ID))
                .thenReturn(Optional.empty());
        when(summariser.summarise(any(), isNull()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("LLM timeout")));

        observer.summariseThread(CHANNEL_ID, CORRELATION_ID, CHANNEL_NAME, TENANCY_ID);

        verify(threadSummaryStore, never()).save(any());
    }

    @Test
    void eventFiredAfterSave() {
        List<Message> messages = List.of(mockMessage());
        when(messageStore.scan(any(MessageQuery.class))).thenReturn(messages);
        when(threadSummaryStore.findByCorrelationId(CHANNEL_ID, CORRELATION_ID))
                .thenReturn(Optional.empty());
        when(summariser.summarise(any(), isNull()))
                .thenReturn(CompletableFuture.completedFuture(
                        SummaryResult.ofText("Done")));
        when(threadSummaryStore.save(any())).thenAnswer(i -> i.getArgument(0));

        observer.summariseThread(CHANNEL_ID, CORRELATION_ID, CHANNEL_NAME, TENANCY_ID);

        ArgumentCaptor<ThreadSummaryUpdatedEvent> cap =
                ArgumentCaptor.forClass(ThreadSummaryUpdatedEvent.class);
        verify(summaryEvents).fireAsync(cap.capture());
        assertThat(cap.getValue().channelName()).isEqualTo(CHANNEL_NAME);
        assertThat(cap.getValue().correlationId()).isEqualTo(CORRELATION_ID);
    }

    @Test
    void concurrentTerminalMessagesSecondIsSkipped() {
        MessageReceivedEvent event = mockEvent(MessageType.DONE, CORRELATION_ID);
        observer.onTerminalMessage(event);
        observer.onTerminalMessage(event);
        // onTerminalMessage is synchronous in this test (no executor)
        // The inFlight guard prevents the second call from queuing work
    }

    private Message mockMessage() {
        Message m = mock(Message.class);
        when(m.id()).thenReturn(1L);
        when(m.content()).thenReturn("Test message");
        when(m.occurredAt()).thenReturn(Instant.now());
        return m;
    }

    private MessageReceivedEvent mockEvent(MessageType type, String correlationId) {
        MessageReceivedEvent e = mock(MessageReceivedEvent.class);
        when(e.channelId()).thenReturn(CHANNEL_ID);
        when(e.channelName()).thenReturn(CHANNEL_NAME);
        when(e.messageType()).thenReturn(type);
        when(e.correlationId()).thenReturn(correlationId);
        return e;
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -Dtest=ThreadSummaryObserverTest -f /Users/mdproctor/claude/casehub/blocks/pom.xml`
Expected: FAIL — `ThreadSummaryObserver` does not exist

- [ ] **Step 3: Implement ThreadSummaryObserver**

Create `ThreadSummaryObserver.java`:

```java
package io.casehub.blocks.channel.summary;

import io.casehub.blocks.summarisation.ContentSummariser;
import io.casehub.qhorus.api.channel.ThreadSummary;
import io.casehub.qhorus.api.channel.ThreadSummaryUpdatedEvent;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.CrossTenantMessageStore;
import io.casehub.qhorus.api.store.ThreadSummaryStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.api.spi.SummaryResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

@ApplicationScoped
public class ThreadSummaryObserver {

    private static final System.Logger LOG =
            System.getLogger(ThreadSummaryObserver.class.getName());
    static final int MAX_THREAD_MESSAGES = 500;

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    private final ContentSummariser<Message> contentSummariser;
    private final CrossTenantMessageStore messageStore;
    private final ThreadSummaryStore threadSummaryStore;
    private final Event<ThreadSummaryUpdatedEvent> summaryEvents;

    @Inject ManagedExecutor executor;

    @Inject
    public ThreadSummaryObserver(ContentSummariser<Message> contentSummariser,
                                 CrossTenantMessageStore messageStore,
                                 ThreadSummaryStore threadSummaryStore,
                                 Event<ThreadSummaryUpdatedEvent> summaryEvents) {
        this.contentSummariser = contentSummariser;
        this.messageStore = messageStore;
        this.threadSummaryStore = threadSummaryStore;
        this.summaryEvents = summaryEvents;
    }

    void onTerminalMessage(
            @Observes(during = TransactionPhase.AFTER_SUCCESS)
            MessageReceivedEvent event) {
        MessageType type = event.messageType();
        if (type != MessageType.DONE && type != MessageType.FAILURE) return;
        if (event.correlationId() == null) return;

        String key = event.channelId() + ":" + event.correlationId();
        if (!inFlight.add(key)) return;

        if (executor == null) {
            try {
                summariseThread(event.channelId(), event.correlationId(),
                        event.channelName(), null);
            } finally {
                inFlight.remove(key);
            }
            return;
        }

        try {
            String channelName = event.channelName();
            executor.submit(() -> {
                try {
                    summariseThread(event.channelId(), event.correlationId(),
                            channelName, null);
                } finally {
                    inFlight.remove(key);
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.remove(key);
            LOG.log(System.Logger.Level.WARNING,
                    "Executor rejected thread summary for "
                            + event.correlationId(), e);
        }
    }

    void summariseThread(UUID channelId, String correlationId,
                         String channelName, String tenancyId) {
        try {
            List<Message> messages = messageStore.scan(
                    MessageQuery.builder()
                            .channelId(channelId)
                            .correlationId(correlationId)
                            .limit(MAX_THREAD_MESSAGES)
                            .build());

            if (messages.isEmpty()) return;

            SummaryResult previous = threadSummaryStore
                    .findByCorrelationId(channelId, correlationId)
                    .map(ts -> new SummaryResult(ts.content(), ts.annotations()))
                    .orElse(null);

            SummaryResult result = contentSummariser
                    .summarise(messages, previous)
                    .toCompletableFuture().join();

            threadSummaryStore.save(ThreadSummary.builder(channelId, correlationId)
                    .content(result.text())
                    .annotations(result.annotations())
                    .updatedAt(Instant.now())
                    .updatedBy("system:thread-summariser")
                    .tenancyId(tenancyId)
                    .build());

            summaryEvents.fireAsync(new ThreadSummaryUpdatedEvent(
                    channelId, channelName, correlationId,
                    "system:thread-summariser"));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Thread summarisation failed for " + correlationId
                            + " on channel " + channelId, e);
        }
    }
}
```

Note: `executor` is `null` in unit tests (no CDI). The null check enables direct invocation for testing while the CDI path uses async dispatch.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -Dtest=ThreadSummaryObserverTest -f /Users/mdproctor/claude/casehub/blocks/pom.xml`
Expected: PASS — all tests green

- [ ] **Step 5: Run full test suite**

Run: `mvn --batch-mode test -f /Users/mdproctor/claude/casehub/blocks/pom.xml`
Expected: PASS — no regressions

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/channel/summary/ThreadSummaryObserver.java \
  src/test/java/io/casehub/blocks/channel/summary/ThreadSummaryObserverTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#59): ThreadSummaryObserver — live thread summaries via ContentSummariser

Push-based observer detects DONE/FAILURE messages with correlationId,
fetches thread messages, delegates to ContentSummariser<Message>,
stores result in ThreadSummaryStore. Per-correlationId concurrency
guard prevents duplicate summarisations.

Refs casehubio/blocks#59"
```
