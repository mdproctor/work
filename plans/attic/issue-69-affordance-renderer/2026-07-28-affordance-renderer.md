# AffordanceRenderer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #69 — AffordanceRenderer — grounded observation rendering for LLM agents
**Issue group:** #69

**Goal:** Add an AffordanceRenderer to casehub-blocks that renders
structured observations with per-entity affordance grounding for LLM
agents.

**Architecture:** Five types in a new sub-package
`io.casehub.blocks.summarisation.observation.affordance`. Two records
model the grounding chain (`Affordance`, `ObservableEntity`). A sealed
interface models observation sections (`ObservationSection` with
`EntityGroup`, `TextBlock`, `ItemList`). A record models the action
vocabulary (`ActionDescriptor`). A concrete class (`AffordanceRenderer`)
renders all of them.

**Tech Stack:** Java 21, JUnit 5, AssertJ. No CDI, no Quarkus runtime.

## Global Constraints

- Package: `io.casehub.blocks.summarisation.observation.affordance`
- Test package: `io.casehub.blocks.summarisation.observation.affordance`
- Build: `mvn --batch-mode test`
- Run single test: `mvn --batch-mode test -pl . -Dtest=ClassName#methodName`
- `@Nullable` from `org.jspecify` (already on classpath)
- All collections defensively copied in compact constructors
- No new dependencies

---

### Task 1: Affordance + ObservableEntity Records

**Files:**
- Create: `src/main/java/io/casehub/blocks/summarisation/observation/affordance/Affordance.java`
- Create: `src/main/java/io/casehub/blocks/summarisation/observation/affordance/ObservableEntity.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/observation/affordance/AffordanceTest.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/observation/affordance/ObservableEntityTest.java`

**Interfaces:**
- Consumes: nothing (leaf types)
- Produces:
  - `Affordance(String actionType, @Nullable String label, @Nullable String requiredItem, List<String> acceptsItems)` — record
  - `Affordance(String actionType, @Nullable String label)` — convenience constructor
  - `ObservableEntity(String id, String displayName, @Nullable String description, List<Affordance> affordances)` — record
  - `ObservableEntity(String id, String displayName, @Nullable String description)` — convenience constructor (empty affordances)

- [ ] **Step 1: Write Affordance tests**

Create `src/test/java/io/casehub/blocks/summarisation/observation/affordance/AffordanceTest.java`:

```java
package io.casehub.blocks.summarisation.observation.affordance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AffordanceTest {

    @Test
    void rejects_null_actionType() {
        assertThatThrownBy(() -> new Affordance(null, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actionType required");
    }

    @Test
    void rejects_blank_actionType() {
        assertThatThrownBy(() -> new Affordance("  ", null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actionType required");
    }

    @Test
    void full_construction() {
        var a = new Affordance("USE", "to apply", "key", List.of("poison", "acid"));
        assertThat(a.actionType()).isEqualTo("USE");
        assertThat(a.label()).isEqualTo("to apply");
        assertThat(a.requiredItem()).isEqualTo("key");
        assertThat(a.acceptsItems()).containsExactly("poison", "acid");
    }

    @Test
    void convenience_constructor() {
        var a = new Affordance("TAKE", "to pick up");
        assertThat(a.actionType()).isEqualTo("TAKE");
        assertThat(a.label()).isEqualTo("to pick up");
        assertThat(a.requiredItem()).isNull();
        assertThat(a.acceptsItems()).isEmpty();
    }

    @Test
    void acceptsItems_defensively_copied() {
        var items = new ArrayList<>(List.of("a", "b"));
        var a = new Affordance("USE", null, null, items);
        items.add("c");
        assertThat(a.acceptsItems()).containsExactly("a", "b");
    }

    @Test
    void acceptsItems_is_unmodifiable() {
        var a = new Affordance("USE", null, null, List.of("a"));
        assertThatThrownBy(() -> a.acceptsItems().add("b"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=AffordanceTest`
Expected: compilation failure — `Affordance` class does not exist.

- [ ] **Step 3: Implement Affordance**

Create `src/main/java/io/casehub/blocks/summarisation/observation/affordance/Affordance.java`:

```java
package io.casehub.blocks.summarisation.observation.affordance;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record Affordance(
        String actionType,
        @Nullable String label,
        @Nullable String requiredItem,
        List<String> acceptsItems) {

    public Affordance {
        if (actionType == null || actionType.isBlank())
            throw new IllegalArgumentException("actionType required");
        acceptsItems = List.copyOf(acceptsItems);
    }

    public Affordance(String actionType, @Nullable String label) {
        this(actionType, label, null, List.of());
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=AffordanceTest`
Expected: all 6 tests PASS.

- [ ] **Step 5: Write ObservableEntity tests**

Create `src/test/java/io/casehub/blocks/summarisation/observation/affordance/ObservableEntityTest.java`:

```java
package io.casehub.blocks.summarisation.observation.affordance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservableEntityTest {

    @Test
    void rejects_null_id() {
        assertThatThrownBy(() -> new ObservableEntity(null, "Name", "desc", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id required");
    }

    @Test
    void rejects_blank_id() {
        assertThatThrownBy(() -> new ObservableEntity("", "Name", "desc", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id required");
    }

    @Test
    void rejects_null_displayName() {
        assertThatThrownBy(() -> new ObservableEntity("x", null, "desc", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayName required");
    }

    @Test
    void rejects_blank_displayName() {
        assertThatThrownBy(() -> new ObservableEntity("x", "  ", "desc", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayName required");
    }

    @Test
    void null_description_allowed() {
        var entity = new ObservableEntity("x", "Name", null, List.of());
        assertThat(entity.description()).isNull();
    }

    @Test
    void full_construction() {
        var affordance = new Affordance("TAKE", "to pick up");
        var entity = new ObservableEntity("poison", "Rat Poison",
                "A dusty bottle", List.of(affordance));
        assertThat(entity.id()).isEqualTo("poison");
        assertThat(entity.displayName()).isEqualTo("Rat Poison");
        assertThat(entity.description()).isEqualTo("A dusty bottle");
        assertThat(entity.affordances()).containsExactly(affordance);
    }

    @Test
    void convenience_constructor_empty_affordances() {
        var entity = new ObservableEntity("x", "Name", "desc");
        assertThat(entity.affordances()).isEmpty();
    }

    @Test
    void affordances_defensively_copied() {
        var list = new ArrayList<>(List.of(new Affordance("TAKE", null)));
        var entity = new ObservableEntity("x", "Name", "desc", list);
        list.add(new Affordance("USE", null));
        assertThat(entity.affordances()).hasSize(1);
    }

    @Test
    void affordances_unmodifiable() {
        var entity = new ObservableEntity("x", "Name", "desc",
                List.of(new Affordance("TAKE", null)));
        assertThatThrownBy(() -> entity.affordances().add(new Affordance("USE", null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **Step 6: Run tests — verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=ObservableEntityTest`
Expected: compilation failure — `ObservableEntity` class does not exist.

- [ ] **Step 7: Implement ObservableEntity**

Create `src/main/java/io/casehub/blocks/summarisation/observation/affordance/ObservableEntity.java`:

```java
package io.casehub.blocks.summarisation.observation.affordance;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record ObservableEntity(
        String id,
        String displayName,
        @Nullable String description,
        List<Affordance> affordances) {

    public ObservableEntity {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id required");
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("displayName required");
        affordances = List.copyOf(affordances);
    }

    public ObservableEntity(String id, String displayName,
                            @Nullable String description) {
        this(id, displayName, description, List.of());
    }
}
```

- [ ] **Step 8: Run tests — verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=ObservableEntityTest`
Expected: all 8 tests PASS.

- [ ] **Step 9: Run all affordance package tests**

Run: `mvn --batch-mode test -pl . -Dtest="AffordanceTest,ObservableEntityTest"`
Expected: all 14 tests PASS.

- [ ] **Step 10: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/summarisation/observation/affordance/Affordance.java \
  src/main/java/io/casehub/blocks/summarisation/observation/affordance/ObservableEntity.java \
  src/test/java/io/casehub/blocks/summarisation/observation/affordance/AffordanceTest.java \
  src/test/java/io/casehub/blocks/summarisation/observation/affordance/ObservableEntityTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#69): Affordance + ObservableEntity records — grounding chain data model"
```

---

### Task 2: ObservationSection + ActionDescriptor

**Files:**
- Create: `src/main/java/io/casehub/blocks/summarisation/observation/affordance/ObservationSection.java`
- Create: `src/main/java/io/casehub/blocks/summarisation/observation/affordance/ActionDescriptor.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/observation/affordance/ObservationSectionTest.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/observation/affordance/ActionDescriptorTest.java`

**Interfaces:**
- Consumes: `ObservableEntity` from Task 1 (used in `EntityGroup`)
- Produces:
  - `ObservationSection` — sealed interface with `header()` method
  - `ObservationSection.EntityGroup(String header, @Nullable String emptyMessage, List<ObservableEntity> entities)` — record
  - `ObservationSection.TextBlock(String header, String content)` — record
  - `ObservationSection.ItemList(String header, @Nullable String emptyMessage, List<String> items)` — record
  - `ObservationSection.entities(...)`, `.text(...)`, `.items(...)` — factory methods
  - `ActionDescriptor(String actionType, String description, @Nullable String parameterFormat)` — record

- [ ] **Step 1: Write ObservationSection tests**

Create `src/test/java/io/casehub/blocks/summarisation/observation/affordance/ObservationSectionTest.java`:

```java
package io.casehub.blocks.summarisation.observation.affordance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservationSectionTest {

    // --- EntityGroup ---

    @Test
    void entityGroup_rejects_null_header() {
        assertThatThrownBy(() -> ObservationSection.entities(null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header required");
    }

    @Test
    void entityGroup_rejects_blank_header() {
        assertThatThrownBy(() -> ObservationSection.entities("  ", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header required");
    }

    @Test
    void entityGroup_factory_creates_correct_type() {
        var entity = new ObservableEntity("x", "X", "desc");
        var section = ObservationSection.entities("Objects", "Nothing here.", List.of(entity));
        assertThat(section).isInstanceOf(ObservationSection.EntityGroup.class);
        assertThat(section.header()).isEqualTo("Objects");
        var eg = (ObservationSection.EntityGroup) section;
        assertThat(eg.emptyMessage()).isEqualTo("Nothing here.");
        assertThat(eg.entities()).containsExactly(entity);
    }

    @Test
    void entityGroup_entities_defensively_copied() {
        var list = new ArrayList<>(List.of(new ObservableEntity("x", "X", "desc")));
        var section = ObservationSection.entities("Objects", null, list);
        list.add(new ObservableEntity("y", "Y", "desc"));
        assertThat(((ObservationSection.EntityGroup) section).entities()).hasSize(1);
    }

    @Test
    void entityGroup_entities_unmodifiable() {
        var section = ObservationSection.entities("Objects", null,
                List.of(new ObservableEntity("x", "X", "desc")));
        assertThatThrownBy(() -> ((ObservationSection.EntityGroup) section).entities()
                .add(new ObservableEntity("y", "Y", "desc")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void entityGroup_null_emptyMessage_allowed() {
        var section = ObservationSection.entities("Objects", null, List.of());
        assertThat(((ObservationSection.EntityGroup) section).emptyMessage()).isNull();
    }

    // --- TextBlock ---

    @Test
    void textBlock_rejects_null_header() {
        assertThatThrownBy(() -> ObservationSection.text(null, "content"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header required");
    }

    @Test
    void textBlock_rejects_null_content() {
        assertThatThrownBy(() -> ObservationSection.text("Header", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content required");
    }

    @Test
    void textBlock_rejects_blank_content() {
        assertThatThrownBy(() -> ObservationSection.text("Header", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content required");
    }

    @Test
    void textBlock_factory_creates_correct_type() {
        var section = ObservationSection.text("Location", "Kitchen: A warm room.");
        assertThat(section).isInstanceOf(ObservationSection.TextBlock.class);
        assertThat(section.header()).isEqualTo("Location");
        assertThat(((ObservationSection.TextBlock) section).content())
                .isEqualTo("Kitchen: A warm room.");
    }

    // --- ItemList ---

    @Test
    void itemList_rejects_null_header() {
        assertThatThrownBy(() -> ObservationSection.items(null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header required");
    }

    @Test
    void itemList_factory_creates_correct_type() {
        var section = ObservationSection.items("Goals", "No goals.", List.of("Find diamond"));
        assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
        assertThat(section.header()).isEqualTo("Goals");
        var il = (ObservationSection.ItemList) section;
        assertThat(il.emptyMessage()).isEqualTo("No goals.");
        assertThat(il.items()).containsExactly("Find diamond");
    }

    @Test
    void itemList_items_defensively_copied() {
        var list = new ArrayList<>(List.of("a"));
        var section = ObservationSection.items("Goals", null, list);
        list.add("b");
        assertThat(((ObservationSection.ItemList) section).items()).hasSize(1);
    }

    @Test
    void itemList_items_unmodifiable() {
        var section = ObservationSection.items("Goals", null, List.of("a"));
        assertThatThrownBy(() -> ((ObservationSection.ItemList) section).items().add("b"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void itemList_null_emptyMessage_allowed() {
        var section = ObservationSection.items("Goals", null, List.of());
        assertThat(((ObservationSection.ItemList) section).emptyMessage()).isNull();
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=ObservationSectionTest`
Expected: compilation failure — `ObservationSection` does not exist.

- [ ] **Step 3: Implement ObservationSection**

Create `src/main/java/io/casehub/blocks/summarisation/observation/affordance/ObservationSection.java`:

```java
package io.casehub.blocks.summarisation.observation.affordance;

import org.jspecify.annotations.Nullable;

import java.util.List;

public sealed interface ObservationSection {

    String header();

    record EntityGroup(
            String header,
            @Nullable String emptyMessage,
            List<ObservableEntity> entities) implements ObservationSection {
        public EntityGroup {
            if (header == null || header.isBlank())
                throw new IllegalArgumentException("header required");
            entities = List.copyOf(entities);
        }
    }

    record TextBlock(
            String header,
            String content) implements ObservationSection {
        public TextBlock {
            if (header == null || header.isBlank())
                throw new IllegalArgumentException("header required");
            if (content == null || content.isBlank())
                throw new IllegalArgumentException("content required");
        }
    }

    record ItemList(
            String header,
            @Nullable String emptyMessage,
            List<String> items) implements ObservationSection {
        public ItemList {
            if (header == null || header.isBlank())
                throw new IllegalArgumentException("header required");
            items = List.copyOf(items);
        }
    }

    static EntityGroup entities(String header, @Nullable String emptyMessage,
                                List<ObservableEntity> entities) {
        return new EntityGroup(header, emptyMessage, entities);
    }

    static TextBlock text(String header, String content) {
        return new TextBlock(header, content);
    }

    static ItemList items(String header, @Nullable String emptyMessage,
                          List<String> items) {
        return new ItemList(header, emptyMessage, items);
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=ObservationSectionTest`
Expected: all 14 tests PASS.

- [ ] **Step 5: Write ActionDescriptor tests**

Create `src/test/java/io/casehub/blocks/summarisation/observation/affordance/ActionDescriptorTest.java`:

```java
package io.casehub.blocks.summarisation.observation.affordance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionDescriptorTest {

    @Test
    void rejects_null_actionType() {
        assertThatThrownBy(() -> new ActionDescriptor(null, "desc", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actionType required");
    }

    @Test
    void rejects_blank_actionType() {
        assertThatThrownBy(() -> new ActionDescriptor("  ", "desc", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actionType required");
    }

    @Test
    void rejects_null_description() {
        assertThatThrownBy(() -> new ActionDescriptor("TAKE", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description required");
    }

    @Test
    void rejects_blank_description() {
        assertThatThrownBy(() -> new ActionDescriptor("TAKE", "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description required");
    }

    @Test
    void full_construction() {
        var d = new ActionDescriptor("MOVE", "Move to a room", "<room-id>");
        assertThat(d.actionType()).isEqualTo("MOVE");
        assertThat(d.description()).isEqualTo("Move to a room");
        assertThat(d.parameterFormat()).isEqualTo("<room-id>");
    }

    @Test
    void null_parameterFormat_allowed() {
        var d = new ActionDescriptor("WAIT", "Do nothing", null);
        assertThat(d.parameterFormat()).isNull();
    }
}
```

- [ ] **Step 6: Run tests — verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=ActionDescriptorTest`
Expected: compilation failure — `ActionDescriptor` does not exist.

- [ ] **Step 7: Implement ActionDescriptor**

Create `src/main/java/io/casehub/blocks/summarisation/observation/affordance/ActionDescriptor.java`:

```java
package io.casehub.blocks.summarisation.observation.affordance;

import org.jspecify.annotations.Nullable;

public record ActionDescriptor(
        String actionType,
        String description,
        @Nullable String parameterFormat) {

    public ActionDescriptor {
        if (actionType == null || actionType.isBlank())
            throw new IllegalArgumentException("actionType required");
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("description required");
    }
}
```

- [ ] **Step 8: Run tests — verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=ActionDescriptorTest`
Expected: all 6 tests PASS.

- [ ] **Step 9: Run all package tests**

Run: `mvn --batch-mode test -pl . -Dtest="AffordanceTest,ObservableEntityTest,ObservationSectionTest,ActionDescriptorTest"`
Expected: all 34 tests PASS.

- [ ] **Step 10: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/summarisation/observation/affordance/ObservationSection.java \
  src/main/java/io/casehub/blocks/summarisation/observation/affordance/ActionDescriptor.java \
  src/test/java/io/casehub/blocks/summarisation/observation/affordance/ObservationSectionTest.java \
  src/test/java/io/casehub/blocks/summarisation/observation/affordance/ActionDescriptorTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#69): ObservationSection sealed interface + ActionDescriptor — section model"
```

---

### Task 3: AffordanceRenderer — Entity Rendering

The core grounding chain rendering: `renderEntities` (both overloads),
internal `renderEntity` and `renderAffordance` methods.

**Files:**
- Create: `src/main/java/io/casehub/blocks/summarisation/observation/affordance/AffordanceRenderer.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/observation/affordance/AffordanceRendererTest.java`

**Interfaces:**
- Consumes: `ObservableEntity`, `Affordance` from Task 1
- Produces:
  - `AffordanceRenderer()` — constructor with default header formatter
  - `AffordanceRenderer(Function<String, String> headerFormatter)` — configurable constructor
  - `String renderEntities(List<ObservableEntity> entities)` — returns "" if empty
  - `String renderEntities(List<ObservableEntity> entities, String emptyMessage)` — returns emptyMessage if empty

- [ ] **Step 1: Write entity rendering tests**

Create `src/test/java/io/casehub/blocks/summarisation/observation/affordance/AffordanceRendererTest.java`:

```java
package io.casehub.blocks.summarisation.observation.affordance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AffordanceRendererTest {

    final AffordanceRenderer renderer = new AffordanceRenderer();

    // --- renderEntities ---

    @Test
    void renderEntities_empty_list_returns_empty_string() {
        assertThat(renderer.renderEntities(List.of())).isEmpty();
    }

    @Test
    void renderEntities_empty_list_with_emptyMessage() {
        assertThat(renderer.renderEntities(List.of(), "Nothing here."))
                .isEqualTo("Nothing here.");
    }

    @Test
    void renderEntities_single_entity_no_affordances() {
        var entity = new ObservableEntity("rack", "Coat Rack", "A wooden rack by the door.");
        assertThat(renderer.renderEntities(List.of(entity)))
                .isEqualTo("- Coat Rack [id: rack]: A wooden rack by the door.");
    }

    @Test
    void renderEntities_entity_null_description() {
        var entity = new ObservableEntity("x", "Widget", null);
        assertThat(renderer.renderEntities(List.of(entity)))
                .isEqualTo("- Widget [id: x]");
    }

    @Test
    void renderEntities_entity_with_one_affordance() {
        var entity = new ObservableEntity("poison", "Rat Poison",
                "A dusty bottle", List.of(new Affordance("TAKE", "to pick up")));
        assertThat(renderer.renderEntities(List.of(entity)))
                .isEqualTo("- Rat Poison [id: poison]: A dusty bottle [TAKE to pick up]");
    }

    @Test
    void renderEntities_entity_with_multiple_affordances() {
        var entity = new ObservableEntity("tea", "Tea Service",
                "A silver set", List.of(
                    new Affordance("INTERACT", null),
                    new Affordance("USE", null, null, List.of("rat-poison"))));
        assertThat(renderer.renderEntities(List.of(entity)))
                .isEqualTo("- Tea Service [id: tea]: A silver set [INTERACT] [USE, with: rat-poison]");
    }

    @Test
    void renderEntities_multiple_entities_newline_separated() {
        var e1 = new ObservableEntity("a", "Alpha", "First");
        var e2 = new ObservableEntity("b", "Beta", "Second");
        var result = renderer.renderEntities(List.of(e1, e2));
        assertThat(result).isEqualTo(
                "- Alpha [id: a]: First\n" +
                "- Beta [id: b]: Second");
    }

    @Test
    void renderEntities_non_empty_list_ignores_emptyMessage() {
        var entity = new ObservableEntity("x", "X", "desc");
        assertThat(renderer.renderEntities(List.of(entity), "Nothing here."))
                .isEqualTo("- X [id: x]: desc");
    }

    // --- Affordance tag formats ---

    @Test
    void affordance_actionType_only() {
        var entity = new ObservableEntity("x", "X", null,
                List.of(new Affordance("LOOK", null)));
        assertThat(renderer.renderEntities(List.of(entity)))
                .contains("[LOOK]");
    }

    @Test
    void affordance_with_label() {
        var entity = new ObservableEntity("x", "X", null,
                List.of(new Affordance("TAKE", "to pick up")));
        assertThat(renderer.renderEntities(List.of(entity)))
                .contains("[TAKE to pick up]");
    }

    @Test
    void affordance_with_requiredItem() {
        var entity = new ObservableEntity("x", "X", null,
                List.of(new Affordance("INTERACT", null, "fake-medal", List.of())));
        assertThat(renderer.renderEntities(List.of(entity)))
                .contains("[INTERACT, requires: fake-medal]");
    }

    @Test
    void affordance_with_label_and_requiredItem() {
        var entity = new ObservableEntity("x", "X", null,
                List.of(new Affordance("INTERACT", "to examine", "fake-medal", List.of())));
        assertThat(renderer.renderEntities(List.of(entity)))
                .contains("[INTERACT to examine, requires: fake-medal]");
    }

    @Test
    void affordance_with_acceptsItems() {
        var entity = new ObservableEntity("x", "X", null,
                List.of(new Affordance("USE", null, null, List.of("rat-poison", "arsenic"))));
        assertThat(renderer.renderEntities(List.of(entity)))
                .contains("[USE, with: rat-poison, arsenic]");
    }

    @Test
    void affordance_with_label_and_acceptsItems() {
        var entity = new ObservableEntity("x", "X", null,
                List.of(new Affordance("USE", "to apply", null, List.of("rat-poison"))));
        assertThat(renderer.renderEntities(List.of(entity)))
                .contains("[USE to apply, with: rat-poison]");
    }

    @Test
    void affordance_with_requiredItem_and_acceptsItems() {
        var entity = new ObservableEntity("x", "X", null,
                List.of(new Affordance("USE", null, "key", List.of("rat-poison"))));
        assertThat(renderer.renderEntities(List.of(entity)))
                .contains("[USE, requires: key, with: rat-poison]");
    }

    @Test
    void affordance_with_all_fields() {
        var entity = new ObservableEntity("x", "X", null,
                List.of(new Affordance("USE", "to apply", "key", List.of("rat-poison"))));
        assertThat(renderer.renderEntities(List.of(entity)))
                .contains("[USE to apply, requires: key, with: rat-poison]");
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=AffordanceRendererTest`
Expected: compilation failure — `AffordanceRenderer` does not exist.

- [ ] **Step 3: Implement AffordanceRenderer with entity rendering**

Create `src/main/java/io/casehub/blocks/summarisation/observation/affordance/AffordanceRenderer.java`:

```java
package io.casehub.blocks.summarisation.observation.affordance;

import java.util.List;
import java.util.function.Function;

public class AffordanceRenderer {

    private static final Function<String, String> DEFAULT_HEADER_FORMATTER =
            header -> "== " + header + " ==";

    private final Function<String, String> headerFormatter;

    public AffordanceRenderer() {
        this(DEFAULT_HEADER_FORMATTER);
    }

    public AffordanceRenderer(Function<String, String> headerFormatter) {
        this.headerFormatter = headerFormatter;
    }

    public AffordanceRenderer withHeaderFormatter(
            Function<String, String> headerFormatter) {
        return new AffordanceRenderer(headerFormatter);
    }

    public String renderEntities(List<ObservableEntity> entities) {
        if (entities.isEmpty()) return "";
        var sb = new StringBuilder();
        for (int i = 0; i < entities.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(renderEntity(entities.get(i)));
        }
        return sb.toString();
    }

    public String renderEntities(List<ObservableEntity> entities,
                                 String emptyMessage) {
        if (entities.isEmpty()) return emptyMessage;
        return renderEntities(entities);
    }

    private String renderEntity(ObservableEntity entity) {
        var sb = new StringBuilder("- ")
                .append(entity.displayName())
                .append(" [id: ").append(entity.id()).append(']');
        if (entity.description() != null) {
            sb.append(": ").append(entity.description());
        }
        for (var affordance : entity.affordances()) {
            sb.append(' ').append(renderAffordance(affordance));
        }
        return sb.toString();
    }

    private String renderAffordance(Affordance affordance) {
        var sb = new StringBuilder("[").append(affordance.actionType());
        if (affordance.label() != null) {
            sb.append(' ').append(affordance.label());
        }
        if (affordance.requiredItem() != null) {
            sb.append(", requires: ").append(affordance.requiredItem());
        }
        if (!affordance.acceptsItems().isEmpty()) {
            sb.append(", with: ").append(
                    String.join(", ", affordance.acceptsItems()));
        }
        sb.append(']');
        return sb.toString();
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=AffordanceRendererTest`
Expected: all 16 tests PASS.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/summarisation/observation/affordance/AffordanceRenderer.java \
  src/test/java/io/casehub/blocks/summarisation/observation/affordance/AffordanceRendererTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#69): AffordanceRenderer entity rendering — grounding chain format"
```

---

### Task 4: AffordanceRenderer — Observation Assembly, Vocabulary, Customization

**Files:**
- Modify: `src/main/java/io/casehub/blocks/summarisation/observation/affordance/AffordanceRenderer.java`
- Modify: `src/test/java/io/casehub/blocks/summarisation/observation/affordance/AffordanceRendererTest.java`

**Interfaces:**
- Consumes: `ObservationSection` (EntityGroup, TextBlock, ItemList) from Task 2; `ActionDescriptor` from Task 2
- Produces:
  - `String renderObservation(List<ObservationSection> sections)` — returns "" if empty or all sections omitted
  - `String renderActionVocabulary(String header, List<ActionDescriptor> actions)` — header rendered as-is
  - `AffordanceRenderer withHeaderFormatter(Function<String, String>)` — already declared in Task 3

- [ ] **Step 1: Write renderObservation tests**

Append to `AffordanceRendererTest.java`:

```java
    // --- renderObservation ---

    @Test
    void renderObservation_empty_sections_returns_empty() {
        assertThat(renderer.renderObservation(List.of())).isEmpty();
    }

    @Test
    void renderObservation_single_entity_section() {
        var entity = new ObservableEntity("poison", "Rat Poison", "A dusty bottle",
                List.of(new Affordance("TAKE", "to pick up")));
        var result = renderer.renderObservation(List.of(
                ObservationSection.entities("Visible Objects", null, List.of(entity))));
        assertThat(result).isEqualTo(
                "== Visible Objects ==\n" +
                "- Rat Poison [id: poison]: A dusty bottle [TAKE to pick up]");
    }

    @Test
    void renderObservation_entity_section_empty_with_message() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.entities("Inventory", "You are carrying nothing.", List.of())));
        assertThat(result).isEqualTo(
                "== Inventory ==\n" +
                "You are carrying nothing.");
    }

    @Test
    void renderObservation_entity_section_empty_null_message_omits() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.entities("Objects", null, List.of())));
        assertThat(result).isEmpty();
    }

    @Test
    void renderObservation_text_section() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.text("Current Location", "Kitchen: A warm room.")));
        assertThat(result).isEqualTo(
                "== Current Location ==\n" +
                "Kitchen: A warm room.");
    }

    @Test
    void renderObservation_item_list_section() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.items("Goals", null,
                        List.of("[PRIMARY] Find diamond", "[SECONDARY] Solve puzzles"))));
        assertThat(result).isEqualTo(
                "== Goals ==\n" +
                "- [PRIMARY] Find diamond\n" +
                "- [SECONDARY] Solve puzzles");
    }

    @Test
    void renderObservation_item_list_empty_with_message() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.items("Goals", "No goals.", List.of())));
        assertThat(result).isEqualTo(
                "== Goals ==\n" +
                "No goals.");
    }

    @Test
    void renderObservation_item_list_empty_null_message_omits() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.items("Goals", null, List.of())));
        assertThat(result).isEmpty();
    }

    @Test
    void renderObservation_mixed_sections_blank_line_separated() {
        var entity = new ObservableEntity("kitchen", "Kitchen", "A warm room",
                List.of(new Affordance("MOVE", "to enter")));
        var result = renderer.renderObservation(List.of(
                ObservationSection.text("Location", "Entrance Hall: Grand foyer."),
                ObservationSection.entities("Exits", null, List.of(entity)),
                ObservationSection.items("Goals", null, List.of("Find diamond"))));
        assertThat(result).isEqualTo(
                "== Location ==\n" +
                "Entrance Hall: Grand foyer.\n\n" +
                "== Exits ==\n" +
                "- Kitchen [id: kitchen]: A warm room [MOVE to enter]\n\n" +
                "== Goals ==\n" +
                "- Find diamond");
    }

    @Test
    void renderObservation_section_ordering_preserved() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.text("B", "second"),
                ObservationSection.text("A", "first")));
        assertThat(result).startsWith("== B ==");
    }

    @Test
    void renderObservation_all_sections_omitted_returns_empty() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.entities("Objects", null, List.of()),
                ObservationSection.items("Goals", null, List.of())));
        assertThat(result).isEmpty();
    }

    @Test
    void renderObservation_omitted_section_no_extra_blank_lines() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.text("A", "first"),
                ObservationSection.entities("B", null, List.of()),
                ObservationSection.text("C", "third")));
        assertThat(result).isEqualTo(
                "== A ==\n" +
                "first\n\n" +
                "== C ==\n" +
                "third");
    }
```

- [ ] **Step 2: Run tests — verify new tests fail**

Run: `mvn --batch-mode test -pl . -Dtest=AffordanceRendererTest`
Expected: new tests fail (renderObservation method does not exist).

- [ ] **Step 3: Implement renderObservation**

Add to `AffordanceRenderer.java` — two methods: `renderObservation` and
private `renderSection`:

```java
public String renderObservation(List<ObservationSection> sections) {
    if (sections.isEmpty()) return "";
    var sb = new StringBuilder();
    boolean first = true;
    for (var section : sections) {
        String rendered = renderSection(section);
        if (rendered == null) continue;
        if (!first) sb.append("\n\n");
        first = false;
        sb.append(headerFormatter.apply(section.header())).append('\n');
        sb.append(rendered);
    }
    return sb.toString();
}

private String renderSection(ObservationSection section) {
    return switch (section) {
        case ObservationSection.EntityGroup eg -> {
            if (eg.entities().isEmpty()) yield eg.emptyMessage();
            yield renderEntities(eg.entities());
        }
        case ObservationSection.TextBlock tb -> tb.content();
        case ObservationSection.ItemList il -> {
            if (il.items().isEmpty()) yield il.emptyMessage();
            var items = new StringBuilder();
            for (int i = 0; i < il.items().size(); i++) {
                if (i > 0) items.append('\n');
                items.append("- ").append(il.items().get(i));
            }
            yield items.toString();
        }
    };
}
```

- [ ] **Step 4: Run tests — verify renderObservation tests pass**

Run: `mvn --batch-mode test -pl . -Dtest=AffordanceRendererTest`
Expected: all tests PASS.

- [ ] **Step 5: Write renderActionVocabulary + header formatter tests**

Append to `AffordanceRendererTest.java`:

```java
    // --- renderActionVocabulary ---

    @Test
    void renderActionVocabulary_multiple_actions() {
        var result = renderer.renderActionVocabulary("Available Actions:", List.of(
                new ActionDescriptor("MOVE", "Move to an adjacent room", "<room-id>"),
                new ActionDescriptor("TAKE", "Pick up a portable object", "<object-id>")));
        assertThat(result).isEqualTo(
                "Available Actions:\n" +
                "- MOVE <room-id>: Move to an adjacent room\n" +
                "- TAKE <object-id>: Pick up a portable object");
    }

    @Test
    void renderActionVocabulary_null_parameterFormat() {
        var result = renderer.renderActionVocabulary("Actions:", List.of(
                new ActionDescriptor("WAIT", "Do nothing this turn", null)));
        assertThat(result).isEqualTo(
                "Actions:\n" +
                "- WAIT: Do nothing this turn");
    }

    @Test
    void renderActionVocabulary_empty_actions_returns_header_only() {
        assertThat(renderer.renderActionVocabulary("Actions:", List.of()))
                .isEqualTo("Actions:");
    }

    @Test
    void renderActionVocabulary_header_not_through_headerFormatter() {
        var custom = new AffordanceRenderer(h -> "## " + h);
        var result = custom.renderActionVocabulary("Available Actions:", List.of(
                new ActionDescriptor("WAIT", "Do nothing", null)));
        assertThat(result).startsWith("Available Actions:");
        assertThat(result).doesNotContain("##");
    }

    // --- withHeaderFormatter ---

    @Test
    void custom_header_formatter_applied_to_sections() {
        var custom = renderer.withHeaderFormatter(h -> "## " + h);
        var result = custom.renderObservation(List.of(
                ObservationSection.text("Location", "Kitchen")));
        assertThat(result).isEqualTo("## Location\nKitchen");
    }

    @Test
    void withHeaderFormatter_returns_new_instance() {
        var custom = renderer.withHeaderFormatter(h -> "## " + h);
        assertThat(custom).isNotSameAs(renderer);
        var original = renderer.renderObservation(List.of(
                ObservationSection.text("Location", "Kitchen")));
        assertThat(original).startsWith("== Location ==");
    }
```

- [ ] **Step 6: Run tests — verify new tests fail**

Run: `mvn --batch-mode test -pl . -Dtest=AffordanceRendererTest`
Expected: new vocabulary tests fail (method does not exist).

- [ ] **Step 7: Implement renderActionVocabulary**

Add to `AffordanceRenderer.java`:

```java
public String renderActionVocabulary(String header,
                                     List<ActionDescriptor> actions) {
    if (actions.isEmpty()) return header;
    var sb = new StringBuilder(header);
    for (var action : actions) {
        sb.append('\n').append("- ").append(action.actionType());
        if (action.parameterFormat() != null) {
            sb.append(' ').append(action.parameterFormat());
        }
        sb.append(": ").append(action.description());
    }
    return sb.toString();
}
```

- [ ] **Step 8: Run all tests — verify everything passes**

Run: `mvn --batch-mode test -pl . -Dtest=AffordanceRendererTest`
Expected: all tests PASS (entity + observation + vocabulary + header formatter).

- [ ] **Step 9: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/summarisation/observation/affordance/AffordanceRenderer.java \
  src/test/java/io/casehub/blocks/summarisation/observation/affordance/AffordanceRendererTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#69): AffordanceRenderer observation assembly + vocabulary + header customization"
```

---

### Task 5: Integration Test + Documentation

**Files:**
- Create: `src/test/java/io/casehub/blocks/summarisation/observation/affordance/AffordanceRendererIntegrationTest.java`
- Modify: `ARC42STORIES.MD` — add affordance sub-package entry
- Modify: `CLAUDE.md` — add affordance sub-package documentation

**Interfaces:**
- Consumes: all types from Tasks 1-4

- [ ] **Step 1: Write integration test**

Create `src/test/java/io/casehub/blocks/summarisation/observation/affordance/AffordanceRendererIntegrationTest.java`:

```java
package io.casehub.blocks.summarisation.observation.affordance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AffordanceRendererIntegrationTest {

    final AffordanceRenderer renderer = new AffordanceRenderer();

    @Test
    void wacky_manor_structural_observation() {
        var observation = renderer.renderObservation(List.of(
                ObservationSection.text("Current Location",
                        "Kitchen: A large room with copper pots hanging from the ceiling."),
                ObservationSection.entities("Visible Objects", "Nothing notable here.", List.of(
                        new ObservableEntity("poison", "Rat Poison",
                                "A dusty bottle of rat poison", List.of(
                                new Affordance("TAKE", "to pick up"))),
                        new ObservableEntity("tea-service", "Tea Service",
                                "A silver tea set on the counter", List.of(
                                new Affordance("INTERACT", null),
                                new Affordance("USE", null, null, List.of("rat-poison")))))),
                ObservationSection.entities("Exits", "No exits.", List.of(
                        new ObservableEntity("entrance-hall", "Entrance Hall",
                                "The grand foyer", List.of(
                                new Affordance("MOVE", "to enter"))),
                        new ObservableEntity("library", "Library",
                                "A dusty library filled with books", List.of(
                                new Affordance("MOVE", "to enter"))))),
                ObservationSection.entities("Characters Present", "You are alone.", List.of()),
                ObservationSection.entities("Your Inventory", "You are carrying nothing.", List.of(
                        new ObservableEntity("rat-poison", "rat-poison", null))),
                ObservationSection.items("Your Goals", "No specific goals.", List.of(
                        "[PRIMARY] Find the Doily Diamond",
                        "[SECONDARY] Solve puzzles"))));

        assertThat(observation)
                .contains("== Current Location ==")
                .contains("Kitchen: A large room with copper pots");

        assertThat(observation)
                .contains("== Visible Objects ==")
                .contains("- Rat Poison [id: poison]: A dusty bottle of rat poison [TAKE to pick up]")
                .contains("- Tea Service [id: tea-service]: A silver tea set on the counter [INTERACT] [USE, with: rat-poison]");

        assertThat(observation)
                .contains("== Exits ==")
                .contains("- Entrance Hall [id: entrance-hall]: The grand foyer [MOVE to enter]");

        assertThat(observation)
                .contains("== Characters Present ==")
                .contains("You are alone.");

        assertThat(observation)
                .contains("== Your Inventory ==")
                .contains("- rat-poison [id: rat-poison]");

        assertThat(observation)
                .contains("== Your Goals ==")
                .contains("- [PRIMARY] Find the Doily Diamond")
                .contains("- [SECONDARY] Solve puzzles");
    }

    @Test
    void wacky_manor_action_vocabulary() {
        var vocab = renderer.renderActionVocabulary("Available Actions:", List.of(
                new ActionDescriptor("MOVE", "Move to an adjacent room", "<room-id>"),
                new ActionDescriptor("TAKE", "Pick up a portable object into your inventory", "<object-id>"),
                new ActionDescriptor("USE", "Use an inventory item on a target object", "<item-id> <target-id>"),
                new ActionDescriptor("INTERACT", "Interact with an object in the environment", "<object-id>"),
                new ActionDescriptor("LOOK", "Look around the current room", null),
                new ActionDescriptor("WAIT", "Do nothing this turn", null)));

        assertThat(vocab)
                .startsWith("Available Actions:")
                .contains("- MOVE <room-id>: Move to an adjacent room")
                .contains("- TAKE <object-id>: Pick up a portable object into your inventory")
                .contains("- USE <item-id> <target-id>: Use an inventory item on a target object")
                .contains("- LOOK: Look around the current room")
                .contains("- WAIT: Do nothing this turn");
    }

    @Test
    void grounding_chain_completeness() {
        var entity = new ObservableEntity("poison", "Rat Poison",
                "A dusty bottle", List.of(
                new Affordance("TAKE", "to pick up"),
                new Affordance("USE", null, null, List.of("tea-service"))));
        var result = renderer.renderEntities(List.of(entity));
        assertThat(result).contains("[id: poison]");
        assertThat(result).contains("[TAKE to pick up]");
        assertThat(result).contains("[USE, with: tea-service]");
    }

    @Test
    void combined_affordance_qualifiers() {
        var entity = new ObservableEntity("altar", "Ancient Altar",
                "A stone altar with runes", List.of(
                new Affordance("USE", "to offer", "key", List.of("gold-coin", "silver-coin"))));
        var result = renderer.renderEntities(List.of(entity));
        assertThat(result).contains("[USE to offer, requires: key, with: gold-coin, silver-coin]");
    }
}
```

- [ ] **Step 2: Run integration tests**

Run: `mvn --batch-mode test -pl . -Dtest=AffordanceRendererIntegrationTest`
Expected: all 4 tests PASS.

- [ ] **Step 3: Run full test suite**

Run: `mvn --batch-mode test`
Expected: all tests PASS (existing + new).

- [ ] **Step 4: Update ARC42STORIES.MD**

Add entry under the existing `io.casehub.blocks.summarisation.observation`
section in §5 Building Block View. Use `ide_read_file` to find the exact
location, then use `Edit` (markdown, not source code).

Add this sub-package entry:

```
### Sub-package: `io.casehub.blocks.summarisation.observation.affordance`

Grounded observation rendering for LLM agents — per-entity affordance
chains (identity + action + consequence) and typed section assembly.

| Class | What it does |
|-------|-------------|
| `ObservableEntity` | Record: entity visible to an agent — id, displayName, description, affordances |
| `Affordance` | Record: action available on an entity — actionType, label, requiredItem, acceptsItems |
| `ObservationSection` | Sealed interface: EntityGroup, TextBlock, ItemList — typed observation sections with factory methods |
| `ActionDescriptor` | Record: action type in the agent's vocabulary — actionType, description, parameterFormat |
| `AffordanceRenderer` | Concrete class: renders entities with grounding chains, assembles observations from sections, renders action vocabulary. Configurable header formatter. |
```

- [ ] **Step 5: Update CLAUDE.md**

Add affordance sub-package documentation to the Key Directories and
Package tables in CLAUDE.md. Use `Edit` (markdown).

Key Directories — add row:
```
| `src/main/java/io/casehub/blocks/summarisation/observation/affordance/` | Affordance grounding — per-entity observation rendering for LLM agents |
| `src/test/java/io/casehub/blocks/summarisation/observation/affordance/` | Tests for affordance rendering |
```

Add new package section after the existing observation sub-package:

```
## Sub-package: `io.casehub.blocks.summarisation.observation.affordance`

Grounded observation rendering for LLM agents. Per-entity affordance chains
(identity + action + consequence) and typed section assembly. Parallel
producer to the temporal observation pipeline — consumer concatenates both.

| Class | What it does |
|-------|-------------|
| `ObservableEntity` | Record: entity visible to an agent `(String id, String displayName, @Nullable String description, List<Affordance> affordances)` |
| `Affordance` | Record: action available on an entity `(String actionType, @Nullable String label, @Nullable String requiredItem, List<String> acceptsItems)`. Compositional tag format: `[ACTION label, requires: item, with: items]` |
| `ObservationSection` | Sealed interface: `EntityGroup` (entities with grounding chains), `TextBlock` (contextual prose), `ItemList` (bulleted items). Factory methods: `entities()`, `text()`, `items()` |
| `ActionDescriptor` | Record: action type in the vocabulary `(String actionType, String description, @Nullable String parameterFormat)` |
| `AffordanceRenderer` | Concrete class: `renderEntities()` (core grounding chains), `renderObservation()` (section assembly), `renderActionVocabulary()` (action vocabulary). Configurable header formatter via `withHeaderFormatter()` |
```

- [ ] **Step 6: Commit documentation**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add ARC42STORIES.MD CLAUDE.md \
  src/test/java/io/casehub/blocks/summarisation/observation/affordance/AffordanceRendererIntegrationTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#69): integration test + ARC42STORIES + CLAUDE.md documentation"
```
