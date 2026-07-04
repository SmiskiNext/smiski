# Tasks

## 1. Dependencies & Configuration

- [x] 1.1 Add `firebase-storage` to `gradle/libs.versions.toml` libraries
      section
- [x] 1.2 Add `retrofit-converter-jackson` (2.12.0) to
      `gradle/libs.versions.toml`
- [x] 1.3 Add `jackson-databind` (2.15.2) to `gradle/libs.versions.toml`
- [x] 1.4 Update `app/build.gradle.kts`: replace `retrofit.gson` with
      `retrofit.converter.jackson`, add `jackson-databind`, add
      `firebase.storage`
- [x] 1.5 Update OpenAPI config in `app/build.gradle.kts`: set
      `serializationLibrary` to `"jackson"`, add `openApiNullable` to `"true"`
- [x] 1.6 Add ProGuard rules to `proguard-rules.pro` for Jackson, JsonNullable,
      and DTO classes

## 2. Jackson Serialization Setup

- [x] 2.1 Create `FirebaseModule.java` in `di/` package: provide
      `FirebaseStorage` singleton
- [x] 2.2 Update `NetworkModule.java`: create `ObjectMapper` with
      `JsonNullableModule` and `NON_NULL` inclusion
- [x] 2.3 Update `NetworkModule.java`: replace `GsonConverterFactory` with
      `JacksonConverterFactory`
- [x] 2.4 Run `./gradlew openApiGenerate` to regenerate DTOs with Jackson
      annotations
- [x] 2.5 Verify build compiles:
      `./gradlew :frontends:android-app:app:assembleDebug`

## 3. Domain Layer

- [x] 3.1 Add `patchMe(String fullName, String username, String avatarUrl)`
      method to `MeRepository.java` interface
- [x] 3.2 Create `UpdateProfileUseCase.java` in `domain/usecase/me/`:
      orchestrates avatar upload + profile patch
- [x] 3.3 Create `AvatarUploadResult.java` record in `domain/model/`: holds
      upload result (url or error)

## 4. Data Layer

- [x] 4.1 Create `AvatarStorageManager.java` in `data/remote/firebase/`: handles
      Firebase Storage upload
- [x] 4.2 Implement `uploadAvatar(String userId, Uri imageUri)` returning
      `CompletableFuture<String>` (public URL)
- [x] 4.3 Implement `deleteAvatar(String userId)` for avatar removal (optional,
      Firebase overwrites)
- [x] 4.4 Implement `patchMe()` in `MeRepositoryImpl.java`: call generated
      `meApi.patchMe()` with `UserManagementPatchUserRequest`
- [x] 4.5 Add `AvatarStorageManager` binding in `RepositoryModule.java` or
      create new `FirebaseModule.java`

## 5. Profile Display (ProfileFragment)

- [x] 5.1 Add `ProfileUiState` sealed interface to `ProfileViewModel.java`
      (Loading, Success, Error)
- [x] 5.2 Add `LiveData<ProfileUiState> profileState` to `ProfileViewModel.java`
- [x] 5.3 Inject `GetMeUseCase` into `ProfileViewModel` and call on init
- [x] 5.4 Update `ProfileFragment.java`: bind `imgAvatar`, `tvName`, `tvEmail`
      views
- [x] 5.5 Observe `profileState` and update UI for Loading/Success/Error states
- [x] 5.6 Implement avatar loading with Glide: circular crop, placeholder, error
      fallback
- [x] 5.7 Create `InitialsDrawable.java` utility: generates initials-based
      avatar from name
- [x] 5.8 Add click listener on `imgAvatar` to navigate to
      AccountSettingsFragment
- [x] 5.9 Update `btnAccountSettings` click listener to navigate to
      AccountSettingsFragment (change from settingsFragment)

## 6. Navigation Setup

- [x] 6.1 Add `accountSettingsFragment` destination to `nav_graph_main.xml`
- [x] 6.2 Add `action_profile_to_accountSettings` action from `profileFragment`
- [x] 6.3 Create `fragment_account_settings.xml` layout with avatar, form
      fields, save button

## 7. Account Settings UI

- [x] 7.1 Create `AccountSettingsFragment.java` in
      `presentation/main/accountsettings/`
- [x] 7.2 Create `AccountSettingsViewModel.java` with `AccountSettingsUiState`
      sealed interface
- [x] 7.3 Implement form state management: track original values, pending
      changes, validation errors
- [x] 7.4 Bind TextInputLayouts for fullName, username, email (email disabled)
- [x] 7.5 Implement inline validation for fullName (required, max 255)
- [x] 7.6 Implement inline validation for username (3-30 chars, pattern
      `[a-zA-Z0-9_-]`)
- [x] 7.7 Implement `hasChanges` logic to enable/disable Save button
- [x] 7.8 Add avatar ImageView with camera overlay badge

## 8. Avatar Picker

- [x] 8.1 Create `layout_avatar_picker_sheet.xml` BottomSheet layout
- [x] 8.2 Create `AvatarPickerSheet.java` BottomSheetDialogFragment
- [x] 8.3 Implement "Take Photo" option with Camera intent via
      ActivityResultContracts
- [x] 8.4 Implement "Choose from Gallery" option with photo picker via
      ActivityResultContracts
- [x] 8.5 Implement "Remove Photo" option (visible only when user has custom
      avatar)
- [x] 8.6 Connect picker results to ViewModel: update pending avatar state

## 9. Save Flow

- [x] 9.1 Implement `saveProfile()` in AccountSettingsViewModel: coordinate
      upload + patch
- [x] 9.2 Handle avatar upload flow: if new image → upload to Firebase → get URL
- [x] 9.3 Handle avatar remove flow: patch with `avatarUrl: null`
- [x] 9.4 Handle profile patch: call `UpdateProfileUseCase.execute()`
- [x] 9.5 Update local session cache on success via
      `SessionRepository.saveSession()`
- [x] 9.6 Show loading state on Save button during save
- [x] 9.7 Show Snackbar "Profile updated" and pop back on success
- [x] 9.8 Handle username conflict error (409): show inline error "Username is
      already taken"
- [x] 9.9 Handle network/upload errors: show Snackbar with Retry action

## 10. Back Navigation & Confirmation

- [x] 10.1 Override `onBackPressed` in AccountSettingsFragment to check for
      unsaved changes
- [x] 10.2 Show MaterialAlertDialog "Discard changes?" when user has unsaved
      changes
- [x] 10.3 Implement "Discard" action: navigate back without saving
- [x] 10.4 Implement "Keep Editing" action: dismiss dialog, stay on screen

## 11. Resources

- [x] 11.1 Create `ic_camera.xml` vector drawable (Material Symbol)
- [x] 11.2 Create `ic_photo_library.xml` vector drawable (Material Symbol)
- [x] 11.3 Create `bg_avatar_edit_badge.xml` drawable (circular badge
      background)
- [x] 11.4 Add string resources: error messages, button labels, dialog text
- [x] 11.5 Add Vietnamese translations for all new strings

## 12. Testing

- [x] 12.1 Write unit tests for `UpdateProfileUseCase`: success, upload failure,
      patch failure
- [x] 12.2 Write unit tests for `AccountSettingsViewModel`: validation, state
      transitions
- [x] 12.3 Write unit tests for `ProfileViewModel`: loading, success, error
      states
- [x] 12.4 Write unit tests for `AvatarStorageManager` with mocked
      FirebaseStorage
- [x] 12.5 Run full test suite: `./gradlew :frontends:android-app:app:test`

## 13. Verification

- [x] 13.1 Build debug APK: `./gradlew :frontends:android-app:app:assembleDebug`
- [ ] 13.2 Manual test: profile loads with real data from API
- [ ] 13.3 Manual test: avatar change flow (gallery + camera)
- [ ] 13.4 Manual test: profile update with all field combinations
- [ ] 13.5 Manual test: error scenarios (network off, invalid input)
- [ ] 13.6 Manual test: back navigation with unsaved changes
- [ ] 13.7 Test with TalkBack enabled for accessibility
