# Project Review and Immersion Recommendations

## Overall Assessment

The project is in a genuinely interesting place. It has moved well beyond a novelty chatbot mod: citizens observe their work, needs, family, colony history, raids, relationships, broadcasts, and rumors. Its strongest idea is that conversation is becoming part of the colony simulation instead of sitting beside it.

The main architectural concern is that there are many good immersion systems, but no single authoritative "citizen mind." Conversation memory, relationships, events, rumors, broadcasts, AI state, and ambient interactions are assembled through several separate paths. This creates a lot of detail, but also opportunities for contradictions and competing interactions.

## Logic Issues to Address First

### 1. Player conversations can evict other player conversations

`ConversationManager.claimSlot` says that player conversations always evict a non-player session. When every slot belongs to a player, however, the implementation evicts the oldest player conversation as a last resort.

This could cause a citizen to suddenly stop responding because somebody elsewhere started speaking. Better options include:

- Allow temporary overflow for player conversations.
- Reject the new conversation with an in-world "give me a moment" response.
- Reserve separate capacities for player and ambient conversations.

Silent eviction is the least immersive outcome.

Relevant code: `src/main/java/me/sshcrack/mc_talking/ConversationManager.java`, around `claimSlot`.

### 2. Urgent contacts are selected with ordering bias

Citizens are checked sequentially, and the first successful random roll wins. This does not reliably select the citizen with the most urgent problem. Entity-list ordering may also cause particular citizens to approach disproportionately often.

Build a weighted candidate list and select once using factors such as:

- Urgency
- Relationship to the player
- Distance
- Time since the citizen last approached
- Personality

A badly injured friend should normally take precedence over a mildly hungry stranger.

The player cooldown is also recorded before slot acquisition or successful conversation startup. A failed start can therefore suppress later legitimate contacts.

Relevant code: `src/main/java/me/sshcrack/mc_talking/handler/UrgentContactHandler.java`.

### 3. Minecraft state may be accessed off the server thread

Flash conversation generation starts a raw thread while passing live citizen entities into the generator. Its completion path also updates synchronized entity state from that thread.

Minecraft and MineColonies objects generally need to be read and mutated on the server thread. A safer flow would be:

1. Capture immutable citizen and world snapshots on the server thread.
2. Perform only network, model, and audio work asynchronously.
3. Schedule entity and status updates back through `server.execute(...)`.

This is the most technically risky issue identified in this review.

Relevant code: `src/main/java/me/sshcrack/mc_talking/conversations/CitizenConversation.java`, especially `performFlashTtsConversation`.

### 4. Ambient conversation is suppressed too broadly

If any nearby citizen is busy, all random citizen conversations around that player are skipped. A single mumbling or occupied citizen can therefore silence an entire visible area.

Busy citizens should be filtered out of the candidate set instead. Other eligible citizens should still be allowed to interact. This would make taverns, workplaces, and town squares feel considerably more alive.

Relevant code: `src/main/java/me/sshcrack/mc_talking/handler/RandomConversationHandler.java`.

### 5. Rumors transfer perfectly and never mutate

Rumors currently preserve the same ID, source, and content through every hop. This is reliable data propagation, but it does not behave much like gossip.

Rumors could carry:

- Confidence
- Hop count
- The person from whom the current citizen directly heard it
- Gradual detail loss or distortion
- Personality-dependent interpretation
- Corrections from first-hand knowledge
- Expiration for mundane or outdated news

A cautious citizen might qualify uncertain information, while a dramatic citizen might exaggerate it.

Relevant code: `src/main/java/me/sshcrack/mc_talking/rumor/RumorMillService.java`.

### 6. Memories are represented as unstructured strings

Facts and events are mutable lists of raw strings that are inserted directly into prompts. This makes it difficult to reason about:

- Age and expiration
- Source and confidence
- Emotional importance
- Whether something was witnessed, overheard, or reported by the player
- Duplicate and contradictory memories
- Prompt injection contained in player-originated text

Introduce a structured `MemoryEntry` model containing time, subject IDs, source, confidence, salience, emotion, and provenance. Prompt rendering should be an implementation detail of a deeper memory module rather than the stored representation.

Relevant code: `src/main/java/me/sshcrack/mc_talking/conversations/memory/data/CitizenMemories.java`.

### 7. Session resumption can preserve stale reality

Gemini session tokens are persisted and resumed, while end-of-conversation summarization is not yet implemented. A resumed model session may retain old state that conflicts with the current world snapshot or freshly generated prompt.

The structured game snapshot should always be authoritative. Sessions should expire reasonably quickly, and explicit summaries are preferable to opaque, long-lived model state.

Relevant code: `src/main/java/me/sshcrack/mc_talking/manager/GeminiWsClient.java`.

## Highest-Value Architectural Improvement

Introduce an `InteractionDirector` module with a small interface such as:

```java
Optional<InteractionIntent> chooseInteraction(WorldSnapshot snapshot);
```

Its implementation would decide whether a citizen should:

- Greet a player
- Complain or ask for help
- Gossip
- Speak to another citizen
- Continue working
- Remain silent

The decision should account for urgency, relationship, personality, current activity, location, time of day, recent interactions, nearby listeners, and narrative novelty.

Currently, this selection is spread across urgent contacts, casual greetings, random conversations, mumbling, rumor talking, broadcasts, and pregeneration. A central director would prevent collisions and repetition while keeping prompt construction, voice generation, and playback in their own modules.

## Additional Immersion Improvements

### Make behavior agree with speech

A citizen asking for planks could look toward the warehouse, walk to the relevant building, or lead the player there. Speech without physical follow-through will eventually feel decorative.

### Add conversational continuity

Citizens should be able to reference interrupted conversations, thank the player after a need is fulfilled, and notice promises that were kept or broken.

### Make silence meaningful

Personality, work pressure, sleep, grief, unfamiliarity, and current danger should affect willingness to talk. More speech is not automatically more immersive.

### Give relationships behavioral consequences

Relationships should influence more than prompt wording. They could affect:

- Greeting and approach distance
- Politeness
- Willingness to disclose personal information
- Who a citizen asks for help
- Trust in rumors
- Whether a citizen initiates conversation at all

### Create social contexts

Prefer interactions connected to a place, time, or activity, such as:

- Tavern conversations
- Coworkers talking during shift changes
- Family conversations at home
- Guards debriefing after raids
- Mourners speaking near graves

These will feel more intentional than uniformly random citizen pairings.

### Separate knowledge from personality

First establish what a citizen knows, including its source and confidence. Then let personality determine how the citizen interprets and expresses that knowledge. This should reduce hallucinated omniscience and make personalities feel more consistent.

### Add lightweight deterministic barks

Short, non-AI reactions to rain, danger, deliveries, collisions, completed buildings, and other common events can provide cheap and reliable responsiveness. Full AI sessions can then be reserved for interactions that benefit from them.

## Suggested Priority

Before adding much more feature breadth, deepen these three modules:

1. Conversation lifecycle and thread safety
2. Interaction selection and scheduling
3. Structured citizen memory and knowledge

Improving these foundations would give the existing feature set much greater coherence. Citizens would feel more like persistent inhabitants of the colony and less like highly informed voice endpoints.
