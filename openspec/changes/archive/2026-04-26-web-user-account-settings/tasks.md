# Tasks

## 1. Confirm contracts and scaffold account settings feature structure

- [x] 1.1 Verify the generated web SDK functions and response types for
      `getMe()`, `putMe()`, `deleteMe()`, and `logout()` align with the planned
      account settings hook inputs and outputs.
- [x] 1.2 Confirm whether a dedicated avatar upload endpoint exists for the web
      app and document the implementation path to use it or the fallback
      `avatarUrl`-only behavior. **No avatar upload endpoint found — implemented
      display-only avatar with local preview (object URLs) and documented the
      limitation in i18n copy.**
- [x] 1.3 Create or update the web account settings feature module structure
      under the workspace profile area for hooks, validation schema, shared
      types, and UI components. ← (verify: planned files exist in the expected
      feature area and match the design component breakdown)

## 2. Replace static profile loading with live account state

- [x] 2.1 Implement a `useUserAccountSettings`-style hook with explicit initial
      load phases, retry handling, normalized API error handling, and separate
      mutation states for save, logout, and delete.
- [x] 2.2 Add mapping utilities or view-model helpers for rendering profile
      summary data, including safe fallbacks for missing optional values such as
      avatar or username.
- [x] 2.3 Update the existing workspace profile screen to call the hook instead
      of using hardcoded account content.
- [x] 2.4 Build localized loading and error-state treatments for the profile
      screen, including retry behavior for failed `getMe()` requests. ← (verify:
      the profile screen never shows hardcoded account values and transitions
      correctly among loading, success, and error states)

## 3. Build the account settings page layout and profile summary surfaces

- [x] 3.1 Implement the main account settings layout using workspace shell
      conventions with responsive section stacking for mobile and desktop
      viewports.
- [x] 3.2 Create the profile summary component to display avatar, full name,
      email, username, auth provider, and relevant account metadata using
      localized labels.
- [x] 3.3 Create reusable section wrappers or cards for editable profile
      content, logout actions, and destructive actions with clear visual
      hierarchy.
- [x] 3.4 Ensure all account settings sections use accessible headings, labels,
      focus order, and keyboard-reachable actions. ← (verify: the rendered
      layout remains usable without horizontal scrolling and exposes accessible
      structure across screen sizes)

## 4. Implement profile edit form and save flow

- [x] 4.1 Create the account settings validation schema with backend-aligned
      constraints for `fullName`, `username`, and `avatarUrl`.
- [x] 4.2 Build the profile edit form with `react-hook-form`, rounded-xl inputs,
      read-only email display, dirty-state handling, and submit/cancel/reset
      behavior consistent with the design.
- [x] 4.3 Wire the form submission flow to `putMe()` and refresh the local
      profile state after successful save.
- [x] 4.4 Surface localized field-level and form-level validation or API errors
      without discarding unsaved input.
- [x] 4.5 Add localized success feedback and ensure saved values become the new
      displayed defaults after a successful update. ← (verify: valid saves
      update summary and form defaults immediately, and failed saves preserve
      the user's edits)

## 5. Implement staged avatar selection and persistence behavior

- [x] 5.1 Add avatar file selection controls with client-side validation for
      JPEG, PNG, and WebP files up to 5 MB.
- [x] 5.2 Add local avatar preview behavior using object URLs and ensure preview
      cleanup when the selected file changes or the form is reset.
- [x] 5.3 Integrate avatar persistence into the save flow according to the
      confirmed backend contract, including the documented fallback if no upload
      endpoint exists. **Fallback implemented: preview-only; avatarUrl field is
      editable but local file upload is not persisted (no upload endpoint
      confirmed).**
- [x] 5.4 Add localized validation and persistence messaging so the UI clearly
      communicates whether avatar changes are only previewed or actually saved.
      ← (verify: invalid files are rejected immediately, preview state behaves
      correctly, and persisted avatar behavior matches the confirmed API
      contract)

## 6. Implement logout and account deletion flows

- [x] 6.1 Wire the existing Sign Out action to `logout()` with pending, success,
      and recoverable failure states.
- [x] 6.2 Reuse or add session cleanup and locale-aware redirect behavior so
      logout clears visible account state and sends the user to the login route.
- [x] 6.3 Create the delete account dialog with exact `DELETE` confirmation
      gating, destructive copy, cancel action, and duplicate-submission
      prevention.
- [x] 6.4 Wire the delete account dialog to `deleteMe()` and keep the dialog
      open with localized error feedback on failure.
- [x] 6.5 Redirect to the login experience after successful account deletion and
      ensure the now-deleted session no longer renders protected account UI. ←
      (verify: logout and delete account both terminate the visible session
      correctly, and delete failures remain recoverable inside the dialog)

## 7. Add localization coverage for the account settings experience

- [x] 7.1 Extend `frontends/web/src/messages/en.json` with account settings
      labels, descriptions, button text, validation strings, loading messages,
      success toasts, and destructive action copy.
- [x] 7.2 Extend `frontends/web/src/messages/vi.json` with equivalent account
      settings translations matching the English key structure.
- [x] 7.3 Replace any remaining static profile copy in the workspace profile
      screen with localized message keys for all newly interactive account
      settings content. ← (verify: English and Vietnamese both cover all account
      settings states without hardcoded strings)

## 8. Test and validate the complete feature

- [x] 8.1 Add unit tests for validation schema, profile state helpers, and any
      error-mapping logic introduced for account settings.
- [x] 8.2 Add component or integration tests for profile loading states, edit
      submission success and failure, avatar validation, logout behavior, and
      delete confirmation gating.
- [ ] 8.3 Add or update E2E coverage for the full account settings journey: load
      profile, edit profile, logout, and delete account flow behavior as
      appropriate for the web test setup. **E2E coverage deferred — no existing
      E2E test suite was found in the project. Unit/component tests provide
      coverage.**
- [x] 8.4 Run the relevant web test, lint, and typecheck commands and resolve
      any regressions before implementation is considered complete. ← (verify:
      the feature is covered by automated tests and passes the project's
      required validation commands)
