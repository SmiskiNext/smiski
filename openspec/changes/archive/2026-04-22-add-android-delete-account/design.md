# Context

The Android app already has an account settings flow for editing profile data
and a logout flow that clears local session data before returning the user to
`WelcomeActivity`. The backend account-deletion API is already implemented and
exposed through the generated Retrofit `MeApi.deleteMe()` method, so the
remaining work is entirely within the Android client.

This change spans the app's clean-architecture layers: a repository contract
update in `domain/repository`, a repository implementation in `data/repository`,
a new domain use case that combines remote deletion with local session cleanup,
and presentation-layer changes in `AccountSettingsViewModel`,
`AccountSettingsFragment`, XML layout, and string resources. The feature must
fit the app's existing patterns: Hilt injection, `CompletableFuture` on
`@IoExecutor`, LiveData-driven UI state, Material 3 dialogs and buttons, and
logout-style task-stack clearing after destructive account actions.

The existing `SessionRepository` already provides `clearAllSessionData()`, which
is suitable for post-deletion cleanup. The account settings screen already
supports inline validation, save-state management, and unsaved-change handling,
so the delete flow should extend the current screen rather than introduce a
separate destination.

## Goals / Non-Goals

**Goals:**

- Add a client-side path for authenticated Android users to delete their own
  account from the existing account settings screen.
- Reuse the generated `MeApi.deleteMe()` endpoint through `MeRepository` and
  follow the app's established asynchronous repository pattern.
- Ensure successful deletion always clears local tokens, cached session/profile
  data, and remember-me state before sending the user back to `WelcomeActivity`
  with a cleared back stack.
- Provide a destructive-action UX that requires explicit confirmation by typing
  `DELETE`, communicates the consequences clearly, and exposes loading, error,
  cancel, and success states through the ViewModel.
- Keep the implementation scoped to Android app behavior only, with no backend
  or cross-service changes.

**Non-Goals:**

- Adding backend API behavior, event consumers, or deletion-related service
  integrations.
- Introducing localization beyond English strings for this change.
- Adding automated tests as part of this OpenSpec change.
- Changing the broader account settings information architecture or moving
  deletion into a separate screen.

## Decisions

### 1. Keep account deletion inside the existing Account Settings screen

The feature will be surfaced as a “Danger Zone” section at the bottom of
`fragment_account_settings.xml`, with a destructive outlined button that opens a
confirmation dialog.

Rationale:

- The user already expects account-level actions inside Account Settings.
- The existing screen is where profile edits happen, so it is the most
  discoverable and consistent place for a destructive account action.
- Reusing the fragment avoids new navigation destinations, menu wiring, and
  duplicated session-clearing logic.

Alternatives considered:

- Add a separate delete-account screen: rejected because the flow is short and
  would add navigation complexity without increasing safety.
- Put deletion on `ProfileFragment`: rejected because the current architecture
  routes profile management through Account Settings, and destructive
  confirmation still needs a dialog.

### 2. Orchestrate remote deletion and local cleanup in a dedicated use case

A new `DeleteAccountUseCase` will depend on `MeRepository` and
`SessionRepository`. Its `execute()` method will call `meRepository.deleteMe()`
and, on success, invoke `sessionRepository.clearAllSessionData()` before
completing.

Rationale:

- This keeps the ViewModel focused on UI state transitions instead of business
  orchestration.
- It matches the app’s clean-architecture layering by composing repository
  operations in the domain layer.
- It creates one clear place to guarantee that local auth/session cleanup
  happens after successful backend deletion.

Alternatives considered:

- Clear session data directly in the fragment after success: rejected because
  cleanup is business behavior, not view logic.
- Clear session data inside `MeRepositoryImpl`: rejected because repository
  responsibilities should remain limited to remote API access.

### 3. Add a dedicated delete-account UI state machine in AccountSettingsViewModel

`AccountSettingsViewModel` will expose a second LiveData stream for a
`DeleteUiState` sealed interface with `Idle`, `Confirming`, `Deleting`,
`Error(message)`, and `Deleted` states. The ViewModel will provide
`requestDeleteAccount()`, `confirmDeleteAccount(String confirmText)`, and
`cancelDelete()` methods.

Rationale:

- The existing screen already uses ViewModel-driven state management, so
  deletion should follow the same pattern.
- A separate state stream avoids overloading the existing profile-edit
  `AccountSettingsUiState` with destructive-flow concerns.
- Distinct states let the fragment control dialog visibility, button enablement,
  retry behavior, and post-success navigation in a predictable way.

Alternatives considered:

- Encode delete state inside the existing content state: rejected because it
  would mix profile-form rendering concerns with transient destructive-dialog
  workflow.
- Handle confirmation text validation entirely in the fragment: rejected because
  the confirmation rule is business-facing behavior that should remain testable
  and centralized in the ViewModel.

### 4. Validate the confirmation string exactly as `DELETE` before sending the API call

The confirm action will remain disabled until the input matches `DELETE`, and
`confirmDeleteAccount()` will also guard against invalid submissions before
entering the deleting state.

Rationale:

- The requirement explicitly calls for case-sensitive confirmation text.
- Double-validating in both the view and ViewModel prevents accidental requests
  from stale or improperly restored UI state.
- Exact-match confirmation is the clearest destructive-action barrier for
  permanent account deletion.

Alternatives considered:

- Case-insensitive matching: rejected because it weakens the explicit
  confirmation requirement.
- Freeform confirmation without typed text: rejected because the action is
  irreversible and needs stronger intent confirmation.

### 5. Follow the existing logout navigation pattern after successful deletion

On `DeleteUiState.Deleted`, `AccountSettingsFragment` will launch
`WelcomeActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`,
matching the existing logout behavior in `ProfileFragment`.

Rationale:

- This guarantees the user cannot navigate back into authenticated screens after
  deletion.
- The pattern already exists in the app and is therefore low-risk and familiar.
- Reusing the same transition reduces implementation ambiguity for a feature
  that ends the authenticated session permanently.

Alternatives considered:

- Pop the navigation stack back to Profile or Splash: rejected because
  authenticated fragments may still remain on the task stack.
- Navigate through NavController only: rejected because the action must clear
  the full activity back stack, not just fragment history.

## Risks / Trade-offs

- Temporary mismatch between dialog UI state and fragment lifecycle events →
  Rebuild dialog behavior from `DeleteUiState` and keep the ViewModel as the
  source of truth for confirmation, loading, and completion.
- Account deletion succeeds remotely but local cleanup fails unexpectedly → Keep
  cleanup limited to existing synchronous
  `SessionRepository.clearAllSessionData()` behavior that is already used for
  logout.
- Users may trigger profile-save and delete-account actions close together →
  Disable destructive controls while deletion is in progress and ensure delete
  flow has a dedicated loading state.
- Extending an existing ViewModel increases complexity → Use a separate sealed
  interface and LiveData stream so delete-account logic remains isolated from
  profile-edit state.
- Snackbar retry after deletion failure could be confusing if the dialog remains
  half-open → Dismiss the dialog on error, show a retry Snackbar from the
  fragment, and let retry re-enter the explicit confirmation flow or call
  confirm again with the preserved input depending on implementation choice.

## Migration Plan

1. Add the new repository contract and implementation method for `deleteMe()`
   without affecting existing profile read/update flows.
2. Add `DeleteAccountUseCase` and inject it into `AccountSettingsViewModel`.
3. Extend the account settings layout, fragment, and strings with the
   danger-zone UI and dialog flow.
4. Verify successful deletion clears local session data and launches
   `WelcomeActivity` with a cleared task stack.
5. If rollback is needed, remove the danger-zone entry point and new use case
   while leaving the backend API unchanged.

## Open Questions

- No open technical questions remain based on the provided requirements. The
  implementation can proceed using the existing backend API, repository
  bindings, and logout navigation pattern.
