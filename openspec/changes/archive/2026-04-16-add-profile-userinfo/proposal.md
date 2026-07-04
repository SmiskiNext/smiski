# Why

The Android app's ProfileFragment currently displays hardcoded placeholder data
("Jane Doe", "<jane.doe@example.com>") instead of the actual authenticated
user's profile. Users cannot view or update their profile information (name,
username, avatar) from the mobile app, despite the backend already supporting
GET/PATCH `/api/v1/me` endpoints.

Additionally, the app uses Gson for Retrofit serialization, which cannot
properly handle `JsonNullable` fields required for PATCH semantics
(distinguishing between "unchanged" vs "set to null"). This blocks proper
profile update functionality.

## What Changes

- **ProfileFragment**: Load real user data via GET `/api/v1/me` and display
  avatar, name, email
- **New AccountSettingsFragment**: Full-screen form for editing profile
  (fullName, username, avatar)
- **Avatar upload**: Integrate Firebase Storage for avatar image upload, then
  PATCH the public URL to backend
- **Gson → Jackson migration**: Switch Retrofit serialization from Gson to
  Jackson to support `JsonNullable` for PATCH requests
- **MeRepository expansion**: Add `patchMe()` method for profile updates
- **Navigation update**: Add new destination and action for
  AccountSettingsFragment

## Capabilities

### New Capabilities

- `android-profile-display`: Display authenticated user's profile (avatar, name,
  email) in ProfileFragment with proper loading/error states
- `android-profile-edit`: Edit user profile (fullName, username, avatar) in
  AccountSettingsFragment with validation and Firebase avatar upload
- `android-jackson-serialization`: Configure Jackson as Retrofit's JSON
  serializer with JsonNullable support for PATCH semantics

### Modified Capabilities

_(none - these are new capabilities, existing specs are not affected)_

## Impact

**Code changes**:

- `frontends/android-app/`: ProfileFragment, ProfileViewModel, new
  AccountSettingsFragment, MeRepository, NetworkModule, navigation graph
- `gradle/libs.versions.toml`: Add firebase-storage, jackson dependencies

**Dependencies added**:

- `com.google.firebase:firebase-storage` (via BOM)
- `com.squareup.retrofit2:converter-jackson:2.12.0`
- `com.fasterxml.jackson.core:jackson-databind:2.15.2`

**Dependencies removed**:

- `com.squareup.retrofit2:converter-gson` (replaced by Jackson)

**Configuration changes**:

- OpenAPI generator: `serializationLibrary: "jackson"` (regenerates DTOs)
- ProGuard rules for Jackson + JsonNullable classes

**Backend**: No changes required (GET/PATCH `/api/v1/me` already implemented)

**Firebase**: Requires Firebase Storage rules configuration for
`/avatars/{userId}.*` path
