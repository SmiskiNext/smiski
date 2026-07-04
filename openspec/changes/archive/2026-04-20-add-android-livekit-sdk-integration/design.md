# Context

The Android app already includes a separate `VideoCallActivity`, a shared
`CallViewModel`, pre-join and active-call fragments, Hilt modules, Retrofit API
clients, and a `CompletableFuture` + executor pattern for asynchronous work.
However, the current call flow is still a shell: `PreJoinFragment` only
validates local form state and navigates forward, `ActiveCallFragment` renders a
static mock video grid, and `CallViewModel` does not manage a real media room.

The backend join flow already expects clients to request meeting entry through
`POST /api/v1.0/meetings/{id}:requestJoin`, then either connect immediately when
approved or wait for server-sent approval events on
`GET /api/v1.0/joinRequests/{requestId}/events`. The Android implementation must
therefore bridge three moving parts: backend approval state, long-lived SSE
subscription, and LiveKit room lifecycle/events.

This is a cross-cutting Android change spanning Gradle configuration,
repositories, DI, ViewModel state, navigation timing, and a substantial active
call UI redesign. It must continue following the Java + XML + Hilt + Clean
Architecture patterns documented in `frontends/android-app/app/codemap.md`.

## Goals / Non-Goals

**Goals:**

- Add the LiveKit Android SDK and required repository/build configuration to the
  Android app module
- Introduce domain and data contracts for backend room-join requests, approval
  SSE handling, and LiveKit room control
- Move call state ownership into `CallViewModel`, including connection state,
  participant/video-track state, and mic/camera toggles backed by LiveKit
- Redesign `ActiveCallFragment` into a production-oriented video-call UI using a
  RecyclerView grid, self-preview overlay, connection quality indicator, and
  updated controls
- Update `PreJoinFragment` to execute the backend join-request flow, including
  waiting-room approval handling for `PENDING` responses

**Non-Goals:**

- Implement Android Picture-in-Picture behavior beyond preserving the existing
  activity shell support
- Implement screen sharing, recording, or background audio services
- Replace the app's existing Java/XML stack with Kotlin, Compose, coroutines, or
  Flow-based state management
- Redesign participants/chat bottom sheets beyond the compatibility changes
  needed to coexist with the new in-call surface

## Decisions

### D1: Split backend join orchestration from LiveKit room control

**Decision:** Create two repository contracts: `JoinRoomRepository` for backend
approval/API/SSE concerns and `LiveKitRepository` for room lifecycle, media
toggles, and participant event propagation.

**Rationale:** The backend join-request flow and the LiveKit media session are
related but not the same responsibility. Keeping them separate preserves Clean
Architecture boundaries and lets `CallViewModel` coordinate approval before room
connection instead of overloading one repository with HTTP, SSE, and RTC logic.

**Alternatives considered:**

- One combined `CallRepository` for API + SSE + LiveKit → simpler surface, but
  mixes transport approval logic with media session state and makes testing less
  focused
- Let fragments call APIs directly and create `Room` objects themselves → breaks
  MVVM and duplicates lifecycle management in UI components

### D2: Model LiveKit state through app-owned view models, not direct SDK objects in XML logic

**Decision:** `CallViewModel` will expose app-owned connection state,
participant lists, local track references, and derived UI flags while internally
subscribing to `LiveKitRepository` callbacks/events.

**Rationale:** The existing call flow already centers state in the shared
activity-scoped ViewModel. Preserving that shape allows fragments to stay thin,
keeps config-change behavior predictable, and avoids binding raw SDK state
directly to fragment view code.

**Alternatives considered:**

- Keep room state inside `ActiveCallFragment` → loses state across fragment/view
  recreation and spreads call logic outside the shared ViewModel
- Expose only raw `Room` and `Participant` SDK objects to fragments → easier at
  first, but increases UI coupling and makes later testing/refactoring harder

### D3: Use OkHttp EventSource with a Handler-driven lifecycle for waiting-room SSE

**Decision:** Implement pending-approval listening with OkHttp SSE/EventSource
and coordinate reconnect/timeout-safe UI updates through the existing Android
main-thread model plus a `Handler`.

**Rationale:** The user explicitly chose a Handler-based SSE approach, and it
fits the current Java app better than introducing reactive dependencies. OkHttp
is already part of the stack, so adding its SSE support keeps networking
consistent.

**Alternatives considered:**

- Poll join-request status over REST → simpler backend integration but slower,
  noisier, and inconsistent with the server's event stream contract
- Introduce RxJava or coroutines for stream handling → inconsistent with the
  existing Java/CompletableFuture codebase

### D4: Preserve the existing CompletableFuture + executor pattern for backend calls, but treat LiveKit room events as callback-driven state updates

**Decision:** `JoinRoomRepository` methods will return `CompletableFuture`
results for request initiation, while `LiveKitRepository` will use listener or
callback registration to push long-lived room events back into `CallViewModel`.

**Rationale:** Backend join initiation is a one-shot async operation and matches
the established repository pattern shown in `MeetingRepositoryImpl`. LiveKit
room events are continuous and cannot be represented cleanly as a single future,
so a callback bridge keeps the implementation aligned with the SDK's real
lifecycle.

**Alternatives considered:**

- Force room state through repeated futures/promises → awkward for reconnect,
  participant churn, and track subscribe/unsubscribe events
- Rewrite existing repositories to streams/observers everywhere → larger
  architectural churn than required for this feature

### D5: Use SurfaceViewRenderer-backed video tiles with RecyclerView and app-managed attachment rules

**Decision:** The active-call grid will move to a RecyclerView-based tile system
whose view holders host `SurfaceViewRenderer` containers, while the adapter
attaches/detaches local and remote video tracks as rows bind or recycle.

**Rationale:** The feature requirements call for `SurfaceViewRenderer` for
performance. RecyclerView is the only practical way to support dynamic
participant counts, active speaker borders, camera-off placeholders, and future
pinning/self-view overlay refinements without hardcoding row layouts.

**Alternatives considered:**

- Keep the current static `LinearLayout` grid → cannot scale to dynamic room
  size or track lifecycle complexity
- Use `TextureViewRenderer` → easier animation in some cases, but lower priority
  than the requested rendering performance

### D6: Keep self-view separate from the main grid

**Decision:** Render the local participant in a draggable 120x160dp overlay
instead of treating self-view as a regular grid tile.

**Rationale:** This preserves more space for remote participants, matches common
video-call UX, and simplifies the dynamic span-count rules because the grid can
focus on remote tiles while the local preview remains independently positioned.

**Alternatives considered:**

- Include self-view inside the grid always → simpler data model, but wastes grid
  space and conflicts with the requested PiP-style overlay
- Hide self-view entirely when camera is on → reduces user confidence in local
  camera state

### D7: Extend the existing video-call shell spec instead of creating a second overlapping in-call capability

**Decision:** Put join-flow and LiveKit-specific behavior into one new
capability (`android-livekit-room-join`) while modifying
`android-videocall-shell` to fully replace the placeholder pre-join/active-call
behavior.

**Rationale:** `android-videocall-shell` already owns the Android call-surface
contract, so changing its requirements avoids spec drift between "shell" and
"real call" behavior. The new capability focuses narrowly on the backend + SDK
integration contract.

**Alternatives considered:**

- Create a second full in-call capability for Android media UI → would duplicate
  ownership with the existing shell spec
- Put everything into the new capability only → would leave old shell
  requirements misleadingly placeholder-oriented

## Risks / Trade-offs

- **LiveKit SDK integration adds build and shrinker complexity** → Mitigation:
  document repository additions, rely on SDK consumer rules where possible, and
  add explicit ProGuard notes only where validation proves necessary
- **RecyclerView video rendering can leak surfaces or leave stale track
  bindings** → Mitigation: define clear attach/detach behavior in
  adapter/view-holder lifecycle and include verification tasks for
  subscription/unsubscription churn
- **SSE streams may outlive the pre-join UI or leak listeners during
  navigation** → Mitigation: centralize subscription ownership in
  repository/ViewModel and require explicit cancellation when approval resolves,
  denial occurs, or the fragment exits
- **Backend approval and LiveKit reconnect state can diverge in error cases** →
  Mitigation: keep join approval completion separate from room connection state,
  and expose a dedicated `FAILED`/`RECONNECTING` model in `CallViewModel`
- **Participants/chat surfaces currently expect placeholder state** →
  Mitigation: scope this change to compatibility updates and preserve existing
  bottom-sheet entry points while allowing richer participant data later

## Migration Plan

1. Add Gradle and dependency updates for LiveKit, JitPack, and SSE support in
   `frontends/android-app/`
2. Introduce domain models/contracts for join requests, approval outcomes,
   connection state, and LiveKit repository events
3. Implement `JoinRoomRepositoryImpl`, SSE client support, and Hilt bindings
4. Implement `LiveKitRepositoryImpl` with room creation, connect/disconnect,
   event observation, and local AV controls
5. Update `CallViewModel` to orchestrate join approval, room connection, and
   participant state
6. Redesign `PreJoinFragment` and `ActiveCallFragment` around the new ViewModel
   contract and navigation timing
7. Add video-grid UI/resources/theme updates and verify app-module build + basic
   manual call flows

Rollback is straightforward on the Android side: revert the new dependencies,
repository bindings, and call UI changes, restoring the existing placeholder
shell without affecting backend APIs.

## Open Questions

- Whether the generated Android OpenAPI client already exposes the exact join
  request DTOs required for `requestJoin`, or whether regenerated models/manual
  wrappers will be needed
- Whether authenticated users should always derive `displayName` from profile or
  still allow an override on pre-join in some future iteration
- Whether connection quality indicators should be derived from LiveKit room,
  participant, or track-level quality signals when multiple sources are present
- Whether the participant bottom sheet should switch from placeholder data to
  the new live participant list in this same implementation pass or in a
  follow-up
