---
title: Your Agent Doesn't Know How It Feels
date: 2026-09-24
author: Mark Proctor
entry_type: note
subtype: diary
tags: [cognitive-agents, OCC, emotions, ALMA, PAD, agent-architecture]
publish: false
---

# Your Agent Doesn't Know How It Feels

Most agent systems treat emotion as a rendering problem. Slap a sentiment label on the output, adjust the tone, move on. The LLM handles the rest.

This is backwards. Emotion is a *computation* problem — and the field solved it decades before anyone was prompting language models.

## The Missing Layer

An agent that tracks your goals, monitors your deadlines, and reminds you about your daughter's birthday needs more than text generation. It needs to *appraise* the situation — to evaluate what's happening against what matters, compute an emotional response, and track that response over time.

Consider what "worry" actually requires:

<div align="center">
<svg viewBox="0 0 700 280" xmlns="http://www.w3.org/2000/svg" style="max-width:680px; font-family: -apple-system, system-ui, sans-serif;">
  <defs>
    <marker id="arrow" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <path d="M0,0 L8,3 L0,6 Z" fill="#555"/>
    </marker>
  </defs>
  <!-- Timeline -->
  <line x1="60" y1="200" x2="640" y2="200" stroke="#999" stroke-width="1.5"/>
  <!-- Day markers -->
  <g font-size="12" fill="#666" text-anchor="middle">
    <line x1="100" y1="195" x2="100" y2="205" stroke="#999"/>
    <text x="100" y="225">Day 1</text>
    <line x1="230" y1="195" x2="230" y2="205" stroke="#999"/>
    <text x="230" y="225">Day 3</text>
    <line x1="430" y1="195" x2="430" y2="205" stroke="#999"/>
    <text x="430" y="225">Day 6</text>
    <line x1="560" y1="195" x2="560" y2="205" stroke="#999"/>
    <text x="560" y="225">Day 7</text>
  </g>
  <!-- Hope line (green, declining) -->
  <polyline points="100,100 230,140 430,175 560,190" fill="none" stroke="#4CAF50" stroke-width="2.5" stroke-dasharray="6,3"/>
  <text x="110" y="90" font-size="13" fill="#4CAF50" font-weight="bold">Hope</text>
  <!-- Fear line (red, rising) -->
  <polyline points="100,170 230,145 430,70 560,40" fill="none" stroke="#E53935" stroke-width="2.5"/>
  <text x="110" y="180" font-size="13" fill="#E53935" font-weight="bold">Fear</text>
  <!-- Pity line (blue, appearing late) -->
  <polyline points="430,130 560,60" fill="none" stroke="#1E88E5" stroke-width="2.5" stroke-dasharray="2,4"/>
  <text x="440" y="125" font-size="13" fill="#1E88E5" font-weight="bold">Pity</text>
  <!-- Annotations -->
  <text x="100" y="250" font-size="10" fill="#888">"Have you thought about a gift?"</text>
  <text x="430" y="250" font-size="10" fill="#888">"I'm worried she'll be upset"</text>
  <text x="530" y="258" font-size="10" fill="#E53935" font-weight="bold">Deadline</text>
  <!-- Y axis -->
  <text x="30" y="45" font-size="11" fill="#666" text-anchor="middle" transform="rotate(-90,30,120)">Intensity</text>
</svg>
</div>

Day 1: gentle reminder, mostly hopeful. Day 3: concern building, hope fading. Day 6: genuine worry — and now empathic concern for the daughter. Day 7: "Mark, I told you last week. I've reminded you every day. I imagine your daughter will be really upset."

That progression isn't tone adjustment. It's a computation: urgency rising, surfacing count incrementing, no progress detected, relationship scores amplifying empathic concern. Each emotion has a type, an intensity, a subject, and a source — intrinsic worry about the agent's own goal to help, empathic pity arising from modelling how the daughter will feel.

## OCC: The 40-Year-Old Answer

Ortony, Clore, and Collins published *The Cognitive Structure of Emotions* in 1988. They defined 22 emotion types as products of cognitive *appraisal* — evaluating events against goals, actions against standards, objects against attitudes. The model was explicitly designed to be computationally tractable.

The 22 types organise into three branches:

| Branch | You're appraising | Valence | Key types |
|--------|------------------|---------|-----------|
| **Events** | consequences for goals | Pleased / Displeased | Hope, Fear, Joy, Distress, Satisfaction, Disappointment, Relief |
| **Agents** | actions by standards | Approving / Disapproving | Pride, Shame, Admiration, Reproach |
| **Objects** | aspects by attitudes | Liking / Disliking | Love, Hate |

The prospect-based emotions — Hope, Fear, Satisfaction, Disappointment, Relief, Fears-confirmed — map directly to a goal lifecycle. A goal starts with Hope. As urgency rises without progress, Fear grows. When the outcome resolves: Satisfaction if it succeeds, Fears-confirmed if it fails, Relief if a feared outcome is narrowly avoided.

This is the birthday scenario. The OCC model doesn't just label the emotions — it specifies the *appraisal variables* that determine intensity: desirability (how much the goal matters), likelihood (how probable the outcome), proximity (how close the deadline), and effort invested (how many times you've tried).

## From Categories to Coordinates

OCC gives you emotion types. But agents also need a continuous emotional space — for mood tracking, personality baselines, and decay dynamics.

Gebhard's ALMA model (2005) bridges this gap with a three-layer architecture:

<div align="center">
<svg viewBox="0 0 600 220" xmlns="http://www.w3.org/2000/svg" style="max-width:580px; font-family: -apple-system, system-ui, sans-serif;">
  <!-- Layers -->
  <rect x="50" y="10" width="500" height="55" rx="6" fill="#E8F5E9" stroke="#4CAF50" stroke-width="1.5"/>
  <rect x="50" y="75" width="500" height="55" rx="6" fill="#E3F2FD" stroke="#1E88E5" stroke-width="1.5"/>
  <rect x="50" y="140" width="500" height="55" rx="6" fill="#FFF3E0" stroke="#FF9800" stroke-width="1.5"/>
  <!-- Labels -->
  <text x="70" y="35" font-size="14" font-weight="bold" fill="#2E7D32">Personality</text>
  <text x="70" y="50" font-size="11" fill="#555">OCEAN traits → PAD baseline (stable, long-term)</text>
  <text x="70" y="100" font-size="14" font-weight="bold" fill="#1565C0">Mood</text>
  <text x="70" y="115" font-size="11" fill="#555">PAD state, decays toward baseline (medium-term)</text>
  <text x="70" y="165" font-size="14" font-weight="bold" fill="#E65100">Emotion</text>
  <text x="70" y="180" font-size="11" fill="#555">OCC appraisal → type + intensity (short-lived, event-driven)</text>
  <!-- Arrows -->
  <path d="M540,65 L540,75" stroke="#666" stroke-width="1.5" marker-end="url(#arrow)"/>
  <path d="M540,130 L540,140" stroke="#666" stroke-width="1.5" marker-end="url(#arrow)"/>
  <text x="555" y="73" font-size="9" fill="#666">calibrates</text>
  <text x="555" y="138" font-size="9" fill="#666">pushes</text>
  <!-- PAD label -->
  <text x="440" y="35" font-size="11" fill="#2E7D32" font-style="italic">P=0.21E + 0.59A + 0.19N</text>
</svg>
</div>

**Layer 1 (Emotion):** OCC appraisal fires, producing a typed emotion with intensity. Fear about the birthday at intensity 0.8. Short-lived — decays within a tick cycle unless re-triggered.

**Layer 2 (Mood):** Emotions push the PAD mood state (Pleasure-Arousal-Dominance). Repeated Fear pushes mood into the Anxious octant. Mood decays exponentially toward the personality baseline.

**Layer 3 (Personality):** Stable traits determine the baseline and calibration. An anxious personality (high arousal baseline) triggers Fear at lower thresholds. ALMA maps Big Five traits to PAD coordinates via Mehrabian's 1996 regression equations.

Each OCC type maps to specific PAD coordinates. Fear is P:-0.64, A:+0.60, D:-0.43 (unpleasant, high-energy, low-control). Hope is P:+0.20, A:+0.20, D:-0.10 (mildly pleasant, alert, slightly submissive). These values come from Gebhard's empirical mapping, validated across agent implementations.

## Two Emotional Systems, Not One

Here's where most agent architectures stop: they compute the agent's emotions about its goals. But there are actually two distinct systems:

**System A — Understanding the user.** The agent models how other people feel. "Mark is probably anxious." This is Theory of Mind — OCC applied to predict *their* emotional states. It uses MentalModel and UserModel components.

**System B — The agent's own emotions.** The agent's cognitive-emotional model, applied to its own goals. "I feel Fear that the birthday will be missed." This includes *empathic* emotions that arise from understanding others — the agent can only feel Pity for the daughter if it has first modelled her likely emotional state via System A.

```
System A: model the daughter → "she expects a gift, won't get one"
              → predict: she'll feel Disappointment
                  ↓
System B: appraise against fortunes-of-others
          → "undesirable event for liked other" → Pity(daughter, 0.6)
```

The Pity is the agent's own emotion — an empathic response to its model of someone else's state. It's not the same as detecting the daughter's sadness. It's the agent *feeling something* because it understands what will happen.

## Recording vs Experiencing

This distinction matters for memory. Today, most agent memory systems are stenographers — recording what the user said, what happened, what was decided. The agent's memory is the user's life story.

But when the agent feels Fear(0.8) for a week, then Relief(0.9) when the gift is finally bought — that Fear→Relief arc is the *agent's own experience*. It should be stored as such:

> "The week before the birthday was stressful. I reminded Mark seven times. Each day my worry grew. When he finally ordered the gift, I felt enormous relief."

That's not a recording of the user's experience. It's the agent's autobiographical memory — domain="agent-experience" rather than domain="experience". A cognitive agent that can recall its own emotional history relates to users differently than one that merely indexes their data.

## Surfacing Tracking: Closing the Loop

The mechanism that drives the Day 1→7 progression is *surfacing tracking*. When the agent renders a goal into the LLM's prompt, it records that act as an event. Neocortex's consolidation pipeline aggregates these into node properties: surfaced-count, first-surfaced-at, last-surfaced-at, last-progress-at.

<div align="center">
<svg viewBox="0 0 660 300" xmlns="http://www.w3.org/2000/svg" style="max-width:640px; font-family: -apple-system, system-ui, sans-serif;">
  <defs>
    <marker id="arr2" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <path d="M0,0 L8,3 L0,6 Z" fill="#555"/>
    </marker>
  </defs>
  <!-- Boxes -->
  <rect x="20" y="20" width="180" height="50" rx="5" fill="#E3F2FD" stroke="#1565C0"/>
  <text x="110" y="42" text-anchor="middle" font-size="12" font-weight="bold" fill="#1565C0">Goal Appraisal</text>
  <text x="110" y="57" text-anchor="middle" font-size="10" fill="#555">OCC → CognitiveEmotion</text>

  <rect x="240" y="20" width="180" height="50" rx="5" fill="#FFF3E0" stroke="#FF9800"/>
  <text x="330" y="42" text-anchor="middle" font-size="12" font-weight="bold" fill="#E65100">LLM Prompt</text>
  <text x="330" y="57" text-anchor="middle" font-size="10" fill="#555">Goals + emotional tone</text>

  <rect x="460" y="20" width="180" height="50" rx="5" fill="#E8F5E9" stroke="#4CAF50"/>
  <text x="550" y="42" text-anchor="middle" font-size="12" font-weight="bold" fill="#2E7D32">User Response</text>
  <text x="550" y="57" text-anchor="middle" font-size="10" fill="#555">Action or silence</text>

  <rect x="460" y="120" width="180" height="50" rx="5" fill="#FCE4EC" stroke="#E53935"/>
  <text x="550" y="142" text-anchor="middle" font-size="12" font-weight="bold" fill="#C62828">Surfacing Record</text>
  <text x="550" y="157" text-anchor="middle" font-size="10" fill="#555">"Surfaced 5×, no progress"</text>

  <rect x="240" y="120" width="180" height="50" rx="5" fill="#F3E5F5" stroke="#7B1FA2"/>
  <text x="330" y="142" text-anchor="middle" font-size="12" font-weight="bold" fill="#6A1B9A">Consolidation</text>
  <text x="330" y="157" text-anchor="middle" font-size="10" fill="#555">Aggregate → node properties</text>

  <rect x="20" y="120" width="180" height="50" rx="5" fill="#FFEBEE" stroke="#E53935"/>
  <text x="110" y="142" text-anchor="middle" font-size="12" font-weight="bold" fill="#C62828">Worry Computation</text>
  <text x="110" y="157" text-anchor="middle" font-size="10" fill="#555">gap/(gap+1) × urgency</text>

  <!-- Arrows -->
  <line x1="200" y1="45" x2="238" y2="45" stroke="#555" stroke-width="1.5" marker-end="url(#arr2)"/>
  <line x1="420" y1="45" x2="458" y2="45" stroke="#555" stroke-width="1.5" marker-end="url(#arr2)"/>
  <line x1="550" y1="72" x2="550" y2="118" stroke="#555" stroke-width="1.5" marker-end="url(#arr2)"/>
  <line x1="458" y1="145" x2="422" y2="145" stroke="#555" stroke-width="1.5" marker-end="url(#arr2)"/>
  <line x1="238" y1="145" x2="202" y2="145" stroke="#555" stroke-width="1.5" marker-end="url(#arr2)"/>
  <line x1="110" y1="118" x2="110" y2="72" stroke="#555" stroke-width="1.5" marker-end="url(#arr2)"/>

  <!-- Cycle label -->
  <text x="330" y="210" text-anchor="middle" font-size="11" fill="#666" font-style="italic">Each tick: appraise → surface → record → consolidate → reappraise</text>
  <text x="330" y="230" text-anchor="middle" font-size="11" fill="#666" font-style="italic">Worry grows with each unacknowledged surfacing</text>
</svg>
</div>

The surfacing-progress gap — surfacings since the last progress event — drives the worry computation. The formula `gap / (gap + 1)` saturates: early surfacings cause rapid worry increase, later ones add diminishing increments. Combined with dynamic urgency from the deadline, this produces natural emotional escalation without hand-tuned thresholds.

When the user finally acts — buys the gift, records progress — the gap resets. Fear drops. If the action came at the last moment: Relief. If it came comfortably early: Satisfaction. If the deadline passes without action: Fears-confirmed plus Pity for the daughter. The OCC taxonomy handles all these transitions with formal specificity.

## What the Research Already Solved

The datasets exist for calibration: ISEAR (7,666 situation descriptions with appraisal data across 37 countries), GoEmotions (58k fine-grained emotion labels), EmoBank (10k VAD-annotated sentences). The architectures exist: ALMA, WASABI, FAtiMA. The mappings exist: OCC→PAD (Gebhard 2005), OCEAN→PAD (Mehrabian 1996). The formalisations exist: Steunebrink's 2009 revision with complete type specifications.

None of this is new. What's new is the application context: agents that persist, that track goals over days, that accumulate emotional state across interactions, that can remember how *they* felt — not just what they were told.

The implementation is open-ended, but the science is settled. Agents that compute emotion through formal appraisal rather than prompt engineering will produce more consistent, more calibrated, and more genuinely responsive behaviour. The OCC model is thirty-eight years old and still the most implemented framework in affective computing. Sometimes the answer has been waiting for the right question.
