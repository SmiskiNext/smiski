# Context

The Android app follows MVVM + Clean Architecture with Hilt DI. Current state:

- `ProfileFragment` displays hardcoded placeholder data, no API integration
- `ProfileViewModel` only handles logout
- `MeRepository` has `getMe()` but no `patchMe()` method
- Network layer uses Gson for serialization, which cannot handle `JsonNullable`
  for PATCH semantics
- Firebase Auth is configured, but Firebase Storage is not integrated
- No existing avatar handling or image picker patterns in the codebase

Backend (already implemented):

- `GET /api/v1/me` returns `UserResponse` (id, email, fullName, username,
  avatarUrl, authProvider, preferences)
- `PATCH /api/v1/me` accepts `PatchUserRequest` with `JsonNullable<String>`
  fields (fullName, avatarUrl, username)

## Goals / Non-Goals

**Goals:**

- Display authenticated user's profile in ProfileFragment (avatar, name, email)
- Allow users to edit profile (fullName, username, avatar) via new
  AccountSettingsFragment
- Upload avatar images to Firebase Storage and update avatarUrl via PATCH API
- Migrate from Gson to Jackson serializer for proper `JsonNullable` support
- Follow existing patterns: UiState for loading/error states, BottomSheet for
  pickers

**Non-Goals:**

- Email editing (read-only, requires re-verification flow)
- Password change (separate feature)
- Username uniqueness check API (handle conflict via PATCH error response)
- Image cropping before upload (v1 - direct upload)
- Preferences editing (separate feature via `/me/preferences`)

## Decisions

### 1. Gson → Jackson Migration

**Decision**: Replace Gson with Jackson for Retrofit serialization across the
entire app.

**Rationale**:

- `JsonNullable` from `jackson-databind-nullable` requires Jackson's
  `JsonNullableModule`
- Gson cannot distinguish "field absent" vs "field null" for PATCH semantics
- Full migration is cleaner than dual-converter setup
- Backend already uses Jackson, ensuring consistency

**Alternatives considered**:

- Custom Gson TypeAdapter for JsonNullable: Complex, not well-documented,
  error-prone
- Dual converter (Gson + Jackson): Adds complexity, increases bundle size,
  requires custom annotations

**Implementation**:

```java
ObjectMapper mapper = new ObjectMapper()
    .registerModule(new JsonNullableModule())
    .setSerializationInclusion(JsonInclude.Include.NON_NULL);

Retrofit.Builder()
    .addConverterFactory(JacksonConverterFactory.create(mapper))
```

### 2. Navigation Structure

**Decision**: Create new `AccountSettingsFragment` separate from existing
`SettingsFragment`.

**Rationale**:

- `SettingsFragment` = App Preferences (language, theme, about)
- `AccountSettingsFragment` = User Profile Editing (avatar, name, username)
- Material Design 3 recommends separating Account from App Settings
- Cleaner navigation and mental model

**Navigation flow**:

```
ProfileFragment
├── Tap avatar → AccountSettingsFragment
├── Tap "Account Settings" → AccountSettingsFragment
└── Tap "Log Out" → WelcomeActivity
```

### 3. Avatar Upload Flow

**Decision**: Upload to Firebase Storage, then PATCH avatarUrl to backend.

**Rationale**:

- Firebase Storage already configured (same project as Firebase Auth)
- Backend doesn't need to handle file uploads
- Client gets direct public URL

**Storage path**: `/avatars/{userId}.jpg` (overwrite on each upload)

**Flow**:

```
1. User picks image (Gallery/Camera)
2. Show preview in form (local URI)
3. On Save: Upload to Firebase → get publicUrl
4. PATCH /me with avatarUrl: publicUrl
5. Update local cache (SessionRepository)
```

### 4. Form Validation Strategy

**Decision**: Inline validation (on text change) + validate on save.

**Rationale**:

- Immediate feedback improves UX
- Backend validation as final gate
- No separate API for username uniqueness check

**Validation rules**: | Field | Rules | Error Messages |
|-------|-------|----------------| | Full Name | Required, 1-255 chars | "Full
name is required" / "Full name too long" | | Username | 3-30 chars,
`[a-zA-Z0-9_-]` | "Must be 3-30 characters" / "Only letters, numbers, \_ and -
allowed" |

### 5. Error Handling

**Decision**: Inline errors for validation, Snackbar for network/upload errors.

| Error Type           | Display                               | Recovery           |
| -------------------- | ------------------------------------- | ------------------ |
| Validation (format)  | Inline error below field              | User fixes input   |
| Username taken (409) | Inline error "Username already taken" | User changes input |
| Upload failed        | Snackbar + Retry action               | Retry button       |
| Network error        | Snackbar + Retry action               | Retry button       |
| Server error (5xx)   | Snackbar "Something went wrong"       | Retry button       |

### 6. Avatar Remove Behavior

**Decision**: Reset to default initials-based avatar.

**Rationale**:

- Better UX than generic placeholder
- Shows user identity even without photo
- PATCH with `avatarUrl: null` clears backend value

**Implementation**: Generate initials drawable based on user's fullName (e.g.,
"Jane Doe" → "JD").

## Data Models

### ProfileUiState (for ProfileFragment)

```java
sealed interface ProfileUiState {
    record Loading() implements ProfileUiState {}
    record Success(String avatarUrl, String fullName, String email) implements ProfileUiState {}
    record Error(String message) implements ProfileUiState {}
}
```

### AccountSettingsUiState (for AccountSettingsFragment)

```java
sealed interface AccountSettingsUiState {
    record Loading() implements AccountSettingsUiState {}
    record Content(
        String avatarUrl,          // Current or pending
        Uri pendingAvatarUri,      // Local URI if changed
        boolean avatarRemoved,     // True if user removed avatar
        String fullName,
        String username,
        String email,              // Read-only
        String fullNameError,      // Validation error or null
        String usernameError,      // Validation error or null
        boolean hasChanges,
        boolean isSaving
    ) implements AccountSettingsUiState {}
    record Error(String message) implements AccountSettingsUiState {}
}
```

## Risks / Trade-offs

### Risk: Gson → Jackson migration breaks existing API calls

**Mitigation**:

- Jackson handles most DTOs identically to Gson
- Run full test suite after migration
- OpenAPI regeneration ensures consistent DTO structure

### Risk: Firebase Storage upload fails silently

**Mitigation**:

- Explicit error handling with user-facing Snackbar
- Retry action available
- Avatar change is optional (user can save other fields)

### Risk: R8 strips Jackson/record metadata in release builds

**Mitigation**:

- Add ProGuard rules for DTO packages and Jackson annotations
- Test release builds before shipping

### Risk: Large avatar images cause slow uploads

**Mitigation**:

- Firebase client library handles compression
- Show upload progress overlay on avatar
- Consider adding client-side resize (v2)

## File Changes Summary

### New Files

```
presentation/main/accountsettings/
├── AccountSettingsFragment.java
├── AccountSettingsViewModel.java
└── AvatarPickerSheet.java

data/remote/firebase/
└── AvatarStorageManager.java

di/
└── FirebaseModule.java

domain/usecase/me/
└── UpdateProfileUseCase.java

res/layout/
├── fragment_account_settings.xml
└── layout_avatar_picker_sheet.xml

res/drawable/
├── bg_avatar_edit_badge.xml
├── ic_camera.xml
└── ic_photo_library.xml
```

### Modified Files

```
gradle/libs.versions.toml          # Add firebase-storage, jackson, converter-jackson
app/build.gradle.kts               # Update dependencies, OpenAPI config
di/NetworkModule.java              # Jackson ObjectMapper + JacksonConverterFactory
domain/repository/MeRepository.java # Add patchMe()
data/repository/MeRepositoryImpl.java # Implement patchMe()
presentation/main/profile/ProfileFragment.java # Load user data, observe state
presentation/main/profile/ProfileViewModel.java # Add profileState, getMe()
res/navigation/nav_graph_main.xml  # Add accountSettingsFragment
res/values/strings.xml             # Add new strings
proguard-rules.pro                 # Jackson + DTO keep rules
```
