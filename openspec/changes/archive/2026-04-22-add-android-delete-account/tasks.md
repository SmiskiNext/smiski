# Tasks

## 1. Data and domain plumbing

- [x] 1.1 Add `deleteMe()` to `MeRepository` and implement it in
      `MeRepositoryImpl` using the existing `CompletableFuture.supplyAsync` +
      `ioExecutor` API-call pattern
- [x] 1.2 Create `DeleteAccountUseCase` to call `meRepository.deleteMe()` and
      then `sessionRepository.clearAllSessionData()` on success
- [x] 1.3 Wire the new use case into the existing account-settings dependency
      flow without changing unrelated profile edit behavior ← (verify:
      successful delete clears local session data only after remote delete
      succeeds, and no existing profile load/save flow regresses)

## 2. ViewModel delete-account state management

- [x] 2.1 Add a `DeleteUiState` sealed interface to `AccountSettingsViewModel`
      with `Idle`, `Confirming`, `Deleting`, `Error(message)`, and `Deleted`
      variants plus exposed LiveData
- [x] 2.2 Implement `requestDeleteAccount()`, `cancelDelete()`, and
      `confirmDeleteAccount(String confirmText)` with exact `DELETE` validation
      and delete-use-case execution
- [x] 2.3 Coordinate delete-account state transitions with existing
      account-settings form state so delete loading, errors, and completion
      remain isolated from save-profile behavior ← (verify: invalid confirmation
      never triggers API calls, deleting state is emitted during request, and
      success/error states transition predictably)

## 3. Account settings UI and resources

- [x] 3.1 Update `fragment_account_settings.xml` to add spacing, divider,
      danger-zone copy, and a destructive `Delete Account` button beneath the
      Save button
- [x] 3.2 Add English string resources for the danger-zone section, confirmation
      dialog copy, typed confirmation hint, loading/error text, and retry action
      labels
- [x] 3.3 Extend `AccountSettingsFragment` to open and control the delete
      confirmation dialog, enable the destructive action only when the input
      equals `DELETE`, disable dialog actions while deleting, dismiss on error,
      show retry Snackbar, and navigate to `WelcomeActivity` with cleared task
      stack on success ← (verify: dialog behavior matches ViewModel state,
      retry/error UX works, and successful deletion prevents back navigation
      into authenticated screens)
