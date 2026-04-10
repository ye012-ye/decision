# Workspace Chat SSE Redesign

## Goal

Keep the existing `/api/chat/stream` SSE contract, but change the workspace chat experience from an event log into a ChatGPT-like conversation surface with:

- one user bubble per submitted prompt
- one assistant bubble that grows during streaming
- a collapsible process panel that preserves `thought`, `action`, and `observation` events
- improved readability and composition in the workspace center column

## Current State

The current frontend already posts to `/api/chat/stream` and parses SSE chunks incrementally. The gap is not protocol support. The gap is presentation and state modeling:

- every SSE event becomes an independent timeline card
- `answer` events append as new cards instead of extending one assistant reply
- `thought`, `action`, and `observation` visually compete with the final answer
- the center column looks like a console log instead of a chat product

This makes the interface feel operational rather than conversational even though the transport is already streaming.

## Constraints

- Keep the current backend event types: `thought`, `action`, `observation`, `answer`, `done`, `error`
- Do not require a backend protocol migration for this change
- Preserve the existing ticket extraction behavior from streamed content
- Preserve session isolation when the active session changes during streaming
- Keep mobile usability intact

## Proposed UX

### Conversation Model

Each user submission becomes one chat turn made of:

- one user message
- one assistant message
- zero or more process entries nested under the assistant message

The main timeline should show only the user and assistant messages as first-class bubbles. Intermediate model/tool activity remains available, but as secondary information inside the assistant message.

### Streaming Behavior

When the user submits a message:

1. create a user message immediately
2. create an assistant draft message immediately
3. stream SSE events into the assistant draft for the originating session

Event handling:

- `answer`: append content into the current assistant message body instead of creating a new top-level item
- `thought`: append a process entry tagged as thought
- `action`: append a process entry tagged as action
- `observation`: append a process entry tagged as observation
- `done`: mark the assistant message as complete and stop the active streaming state
- `error`: mark the assistant message as failed, show the error text inline, and expand the process panel

If a stream completes without any `answer` payload, the assistant message should show a fallback text indicating that no final reply was returned, while still preserving process entries.

### Process Disclosure

Assistant messages should expose a secondary disclosure control when process entries exist.

- Default state: collapsed
- Label: short utility copy such as `查看过程` plus count
- Expanded state: list of process rows in arrival order
- Error state: auto-expanded so failures are visible

The disclosure control should feel clearly secondary to the main reply. The final answer remains the dominant visual element.

## Proposed State Model

Replace the center-column event-log shape with a message-oriented model in the workspace store.

Recommended structures:

- `ChatMessage`
  - `id`
  - `role`: `user | assistant`
  - `content`
  - `status`: `streaming | done | error`
  - `processEntries`
  - `processExpanded`
- `ProcessEntry`
  - `id`
  - `type`: `thought | action | observation`
  - `content`

Session state should keep `messages` rather than a flat `events` array. The store may keep an internal mapping from session id to the currently open assistant draft if that simplifies event routing.

This preserves the current session-based architecture while making the rendering layer reflect real conversational turns.

## Component Changes

### Workspace Store

Refactor `workspace` store message handling so that:

- sending creates the user message and assistant draft before the request starts
- streamed events mutate the assistant draft for the session that initiated the request
- ticket extraction still inspects streamed text and updates only the originating session context
- switching the active session mid-stream does not redirect incoming events

### Chat Timeline

Replace the current event-log rendering with a message list.

- user bubble aligned to the right
- assistant bubble aligned to the left
- assistant bubble body styled for longer reading
- nested process disclosure rendered inside or directly below the assistant message container
- empty state copy adjusted to fit a conversational workspace rather than an operations log

### Composer

Restyle the composer to be closer to a modern chat product:

- larger radius
- quieter chrome
- clearer input affordance
- less control-panel framing

Behavior stays simple:

- disabled during send
- submit on button press
- current textarea flow remains unless there is already an established keyboard shortcut pattern elsewhere in the app

## Visual Direction

Keep the existing dark palette family, but reduce the control-room look.

- reduce hard borders and colored side rails on each message
- increase whitespace and line-height
- make assistant text blocks visually calmer and wider
- keep process UI in a muted surface layer
- preserve strong contrast and mobile readability

The workspace center column should read as a dialogue surface first and an instrumentation surface second.

## Scrolling Behavior

The chat timeline should auto-scroll to the bottom while the user is already near the bottom. If the user scrolls upward to inspect history, the UI should stop forcing scroll jumps until they return near the bottom.

This avoids the common failure mode where streaming content steals position during manual review.

## Testing Strategy

### Store Tests

Update unit tests so they verify:

- sending creates one user message and one assistant draft
- multiple streamed `answer` payloads accumulate into the same assistant message
- process events are stored under the assistant message
- session switching during streaming keeps updates on the originating session
- ticket extraction still updates the originating session context

### Component Tests

Add or update component coverage so it verifies:

- the timeline renders user and assistant messages as primary content
- the process disclosure only appears when process entries exist
- disclosure toggling shows and hides process rows
- error states keep process details visible

### End-to-End Tests

Adjust the existing console flow to verify:

- user input creates a user bubble
- streamed assistant reply becomes visible in the main bubble
- process disclosure is present and expandable
- extracted work-order context still appears in the side panel
- desktop and mobile layouts remain usable

## Out of Scope

- changing backend SSE event names or payload schema
- token-level typing animation beyond natural stream growth
- markdown rendering upgrades
- multi-assistant parallel responses
- persistence changes beyond the current in-memory/session store pattern

## Risks and Mitigations

### Risk: Backend sends one complete `answer`

Mitigation: appending into the assistant body still works; the UX simply appears to complete in one chunk.

### Risk: No `answer` event is returned

Mitigation: show a fallback assistant message and keep process entries accessible.

### Risk: Message model refactor breaks existing tests

Mitigation: update store and e2e coverage first around the new message structure before final UI polish.

## Implementation Summary

This change should be implemented as a frontend-only refactor centered on the workspace store, chat timeline, and related tests. The protocol remains SSE over `/api/chat/stream`, but the UI will stop presenting SSE as a raw event log and instead treat it as material for a single streaming assistant reply with a collapsible process trail.
