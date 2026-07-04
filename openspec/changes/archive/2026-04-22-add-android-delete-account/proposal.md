# Why

The Android app currently lets users log out or edit profile details, but it
does not provide a way for users to permanently delete their account from mobile
even though the backend endpoint already exists. Adding a native delete-account
flow closes that gap, ensures local session data is cleared after deletion, and
gives users a clear, safe, and explicit destructive-action experience.

## What Changes

- Add delete-account support to the Android `MeRepository` so the app can call
  `DELETE /api/v1/me` through the existing generated Retrofit client.
- Introduce a `DeleteAccountUseCase` that orchestrates remote account deletion
  and local session cleanup in a single domain workflow.
- Extend `AccountSettingsViewModel` with delete-account state management,
  confirmation handling, input validation, loading, error, and success states.
- Update `AccountSettingsFragment` and `fragment_account_settings.xml` to add a
  Material 3 “Danger Zone” section and a confirmation dialog that requires
  typing `DELETE` before proceeding.
- Add English string resources for the destructive flow and reuse the existing
  logout-style navigation pattern to return the user to `WelcomeActivity` after
  success.

## Capabilities

### New Capabilities

- `android-account-deletion`: Allow authenticated Android users to permanently
  delete their account from Account Settings with explicit confirmation,
  destructive-action UI states, local session cleanup, and navigation back to
  the unauthenticated welcome flow.

### Modified Capabilities

- `android-profile-edit`: Extend the account settings experience to include a
  destructive account-deletion entry point and related account-settings screen
  behavior.

## Impact

**Affected code**

- `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/domain/repository/MeRepository.java`
- `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/data/repository/MeRepositoryImpl.java`
- `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/domain/usecase/me/DeleteAccountUseCase.java`
- `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/presentation/main/accountsettings/AccountSettingsViewModel.java`
- `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/presentation/main/accountsettings/AccountSettingsFragment.java`
- `frontends/android-app/app/src/main/res/layout/fragment_account_settings.xml`
- `frontends/android-app/app/src/main/res/values/strings.xml`

**APIs and systems**

- Reuses existing backend endpoint `DELETE /api/v1/me`
- Reuses existing generated Retrofit `MeApi.deleteMe()` method
- Reuses existing local auth/session infrastructure through `SessionRepository`

**Dependencies**

- No new backend work or third-party dependencies are required for this change.
