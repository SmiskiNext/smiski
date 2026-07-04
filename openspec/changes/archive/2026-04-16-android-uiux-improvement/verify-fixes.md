# Verify Fixes Log

## [2026-04-14] Round 1 (from opsx-verify auto-fix)

### opsx-arch-verifier

- Fixed: [CRITICAL] Removed stale import of non-existent `VideoCallActivity` and
  unused `Intent` import from `DashboardFragment.java` — compile blocker
  resolved
- Fixed: [CRITICAL] Updated `LoginFragment.java` to navigate to
  `MainActivity.class` instead of `DashboardActivity.class` — migration now
  wired correctly
- Fixed: [CRITICAL] Added `MainActivity` declaration to `AndroidManifest.xml` —
  runtime crash resolved

### opsx-uiux-verifier

- Fixed: [CRITICAL] Added `clickable="true"`, `focusable="true"`, and
  `selectableItemBackgroundBorderless` to settings button FrameLayout in
  `fragment_dashboard.xml`
- Fixed: [CRITICAL] Added `clickable="true"`, `focusable="true"`, and
  `selectableItemBackgroundBorderless` to search button FrameLayout in
  `fragment_calendar.xml`
- Fixed: [CRITICAL] Added `clickable="true"`, `focusable="true"`, and
  `selectableItemBackgroundBorderless` to month navigation buttons (prev/next)
  in `fragment_calendar.xml`
- Fixed: [CRITICAL] Wrapped calendar avatar (36dp) in 48dp FrameLayout to meet
  touch target minimum in `fragment_calendar.xml`

## [2026-04-14] Round 2 (from opsx-apply Phase 4-5 milestone verification)

### opsx-uiux-verifier

- Fixed: [CRITICAL] Added missing `spacing_xxs` (2dp) token to `dimens.xml` —
  build error resolved
- Fixed: [CRITICAL] Replaced hardcoded `#80000000` color with
  `@color/overlay_scrim` in `fragment_create_meeting.xml` — dark mode compatible
- Fixed: [CRITICAL] Changed `edtDate` and `edtTime` fields in
  `fragment_schedule.xml` from `focusable="false"` to
  `focusable="true" focusableInTouchMode="false"` — keyboard/TalkBack accessible
- Fixed: [CRITICAL] Changed video/audio icon `contentDescription` to
  `importantForAccessibility="no"` in `fragment_create_meeting.xml` — prevents
  TalkBack double-announcement
- Fixed: [CRITICAL] Replaced hardcoded `56dp` margin with
  `@dimen/list_item_icon_offset` token in `fragment_settings.xml`
- Fixed: [WARNING] Added `button_height_lg` (60dp), `icon_container_md` (40dp),
  `icon_size_xs` (14dp), `list_item_icon_offset` (56dp) tokens to `dimens.xml`
- Fixed: [WARNING] Replaced hardcoded `60dp` button heights with
  `@dimen/button_height_lg` in `fragment_schedule.xml`,
  `fragment_create_meeting.xml`, `fragment_join_meeting.xml`
- Fixed: [WARNING] Replaced hardcoded `40dp` icon containers with
  `@dimen/icon_container_md` in `fragment_create_meeting.xml`
- Fixed: [WARNING] Replaced hardcoded `14dp` icon size with
  `@dimen/icon_size_xs` in `fragment_create_meeting.xml`
- Fixed: [SUGGESTION] Replaced `app:strokeColor="@android:color/transparent"`
  with `app:strokeWidth="0dp"` in `fragment_create_meeting.xml`
- Added: `overlay_scrim` color (#80000000) to `colors.xml`

## [2026-04-14] Round 3 (from opsx-arch-verifier manual verification)

### opsx-arch-verifier

- Fixed: [CRITICAL] Renamed BottomNav menu IDs in `bottom_nav_menu.xml` to match
  nav graph destination IDs (`nav_home` → `dashboardFragment`, `nav_calendar` →
  `calendarFragment`, `nav_profile` → `profileFragment`) — NavigationUI tab
  selection now works correctly
- Fixed: [WARNING] Changed settings button navigation in
  `DashboardFragment.java` from raw `R.id.profileFragment` to action
  `R.id.action_dashboard_to_settings` — proper back-stack behavior, goes
  directly to Settings
- Added: `action_dashboard_to_settings` action in `nav_graph_main.xml`
- Fixed: [WARNING] Added TODO comment in `ScheduleFragment.java` explaining
  intentional cross-package ViewModel reuse for legacy back-compat
- Fixed: [WARNING] Removed hardcoded meeting URL from
  `CreateMeetingFragment.java` — clipboard logic removed until ViewModel
  provides actual meeting link, unused imports (ClipData, ClipboardManager,
  Context) removed

## [2026-04-14] Round 4 (from opsx-apply Section 6 milestone verification)

### opsx-arch-verifier

- Fixed: [CRITICAL] Added null check for `navController` in
  `VideoCallActivity.onSupportNavigateUp()` — prevents NPE on back navigation
  before layout is fully attached
- Fixed: [WARNING] Added `Locale.ROOT` to `String.format()` in
  `ActiveCallFragment.java:146` — timer displays ASCII digits on all locales

### opsx-uiux-verifier

- Fixed: [CRITICAL] Added `minHeight="@dimen/touch_target_min"`,
  `clickable="true"`, `focusable="true"`, and `gravity="center"` to `btnLeave`
  TextView in `fragment_active_call.xml` — meets 48dp touch target minimum
- Fixed: [CRITICAL] Changed `btnSpeaker` contentDescription from `cd_settings`
  to `cd_speaker` in `fragment_active_call.xml` — correct accessibility label
- Fixed: [CRITICAL] Changed `btnAttach` contentDescription from `cd_settings` to
  `cd_attach_file` in `layout_meeting_chat_sheet.xml` — correct accessibility
  label
- Fixed: [CRITICAL] Changed `btnSend` contentDescription from `call_chat` to
  `cd_send_message` in `layout_meeting_chat_sheet.xml` — correct accessibility
  label
- Fixed: [CRITICAL] Changed `btnMore` contentDescription from `cd_settings` to
  `cd_more_options` in `layout_meeting_chat_sheet.xml` — correct accessibility
  label
- Added: New accessibility strings `cd_speaker`, `cd_send_message`,
  `cd_attach_file`, `cd_close`, `cd_more_options` to `strings.xml`

## [2026-04-14] Round 5 (from opsx-apply Sections 7-9 milestone verification)

### opsx-arch-verifier

- Fixed: [WARNING] Refactored all `drawable/` shape XMLs to use `?attr/` theme
  attributes instead of `@color/md_theme_light_*` — eliminates need for
  `drawable-night/` variants and prevents dual-maintenance debt
- Fixed: Deleted redundant `drawable-night/` variants for bg_circle_white,
  bg_rounded_gray, bg_circle_blue, bg_leave_button, bg_badge_red, bg_dot_blue,
  bg_image_placeholder, bg_chat_input, bg_chat_outgoing, bg_chat_incoming — now
  handled by ?attr/ in base drawable

### opsx-uiux-verifier

- Fixed: [CRITICAL][REGRESSION] Changed `btnMore` contentDescription from
  `cd_settings` back to `cd_more_options` in `fragment_active_call.xml:400` —
  TalkBack now announces correct label
- Fixed: [CRITICAL] Replaced `@color/video_placeholder_purple` with
  `?attr/colorPrimaryContainer` and `@color/video_placeholder_orange` with
  `?attr/colorSurfaceVariant` in `fragment_active_call.xml` — video grid
  placeholders now theme-aware
- Fixed: [CRITICAL] Replaced `@color/white` with `?attr/colorOnPrimary` for icon
  tint and text color in "You" badge overlay in `fragment_create_meeting.xml` —
  badge now uses theme system
- Fixed: [CRITICAL] Refactored `bg_chat_input.xml`, `bg_chat_outgoing.xml`,
  `bg_chat_incoming.xml` to use `?attr/` theme attributes — chat UI now renders
  correctly in dark mode
- Removed: Unused `video_placeholder_purple` and `video_placeholder_orange`
  colors from `colors.xml`

## [2026-04-14] Round 6 (from opsx-apply verification fixes)

### opsx-arch-verifier

- Fixed: [CRITICAL] Removed direct `VideoCallActivity` cast from
  `PreJoinFragment.java:106,152` — now reads `isGuest` and `meetingCode` from
  `CallViewModel` instead of casting to concrete Activity class, follows
  Dependency Inversion Principle
- Fixed: [CRITICAL] Added `initializeViewModelFromIntent()` to
  `VideoCallActivity.onCreate()` — pushes intent extras (`isGuest`,
  `meetingCode`) into `CallViewModel` so fragments can read from ViewModel
- Fixed: [WARNING] Moved call timer from `ActiveCallFragment` to `CallViewModel`
  — timer now survives configuration changes, uses `startCallTimer()` /
  `stopCallTimer()` / `getCallDuration()` pattern
- Fixed: [WARNING] Deleted duplicate legacy ViewModel classes:
  `presentation/dashboard/DashboardViewModel.java`,
  `presentation/calendar/CalendarViewModel.java`,
  `presentation/profile/ProfileViewModel.java` — dead code removed
- Fixed: [WARNING] Changed `ParticipantsBottomSheet.setupObservers()` to reuse
  existing `ParticipantAdapter` via `adapter.updateList()` instead of creating
  new adapter on every LiveData emission — RecyclerView no longer flickers
- Fixed: [WARNING] Replaced 6 `Color.parseColor()` calls in
  `ParticipantAdapter.onBindViewHolder()` with `MaterialColors.getColor()` theme
  attribute lookups — participant list icons now theme-aware for dark mode
- Fixed: [WARNING] Changed `Locale.getDefault()` to `Locale.ROOT` in
  `ScheduleFragment.java:129,154` for date/time `String.format()` — programmatic
  fields now use ASCII digits on all locales

### opsx-uiux-verifier

- Fixed: [CRITICAL] Rewrote `item_participant.xml` — replaced
  `@android:color/black` with `?attr/colorOnSurface`, `#1877F2` with
  `?attr/colorPrimary`, `#999999` with `?attr/colorOnSurfaceVariant`, wrapped
  36dp icon buttons in 48dp FrameLayout touch targets, added
  `contentDescription`, replaced system drawables with Material icons
- Fixed: [CRITICAL] Added `minHeight`, `clickable`, `focusable`, and
  `selectableItemBackgroundBorderless` to `tvSeeAll` in `fragment_dashboard.xml`
  — touch target and interaction states now meet accessibility requirements
- Fixed: [WARNING] Changed `btnClose` contentDescription from `cd_back` to
  `cd_close` in `layout_meeting_chat_sheet.xml` and
  `layout_participants_sheet.xml` — correct semantic label for dismiss action
- Fixed: [WARNING] Changed `btnFloatChat` contentDescription from `call_chat` to
  `cd_open_chat` in `fragment_active_call.xml` — imperative action label
- Fixed: [SUGGESTION] Changed `btnSpeaker` drawable from `ic_settings` to
  `ic_volume_up` in `fragment_active_call.xml` — icon now matches function
- Added: `ic_volume_up.xml` Material Symbol to `drawable/`
- Added: `float_button_sm` (56dp), `float_button_lg` (64dp), `chat_input_height`
  (50dp), `video_grid_bottom_clearance` (100dp), `list_item_time_offset` (80dp)
  dimension tokens to `dimens.xml`
- Added: `cd_open_chat`, `permission_camera_mic_required`, `call_muted_all`,
  `call_attachment_coming_soon`, `call_message_sent` strings to `strings.xml`
  and Vietnamese translations to `values-vi/strings.xml`
