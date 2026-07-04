# Purpose

Define string-resource, localization, and accessibility-text requirements for
the Android main app shell and video call surfaces.

# ADDED Requirements

## Requirement: Dashboard Strings

All UI text in Dashboard SHALL use string resources.

### Scenario: Dashboard header strings

- **WHEN** Dashboard is displayed
- **THEN** app title SHALL use `@string/app_name` or `@string/dashboard_title`
- **THEN** it SHALL NOT use hardcoded "Meet AI"

### Scenario: Quick action strings

- **WHEN** Dashboard quick actions are displayed
- **THEN** "New Meeting" SHALL use `@string/action_new_meeting`
- **THEN** "Start instantly" SHALL use `@string/action_new_meeting_subtitle`
- **THEN** "Join Meeting" SHALL use `@string/action_join_meeting`
- **THEN** "With a code" SHALL use `@string/action_join_meeting_subtitle`
- **THEN** "Schedule" SHALL use `@string/action_schedule`
- **THEN** "Plan ahead" SHALL use `@string/action_schedule_subtitle`

### Scenario: Upcoming meetings section strings

- **WHEN** upcoming meetings section is displayed
- **THEN** "Upcoming Meetings" SHALL use `@string/section_upcoming_meetings`
- **THEN** "See all" SHALL use `@string/action_see_all`

### Scenario: Meeting card action strings

- **WHEN** meeting cards are displayed
- **THEN** "Join" button SHALL use `@string/action_join`
- **THEN** "Wait" button SHALL use `@string/action_wait`

## Requirement: Calendar Strings

All UI text in Calendar SHALL use string resources.

### Scenario: Calendar header strings

- **WHEN** Calendar is displayed
- **THEN** title "Meeting Calendar" SHALL use `@string/calendar_title`

### Scenario: Weekday abbreviations

- **WHEN** weekday headers are displayed
- **THEN** they SHALL use string array `@array/weekday_abbreviations`
- **THEN** array SHALL contain: M, T, W, T, F, S, S (or localized equivalents)

### Scenario: Calendar status strings

- **WHEN** meeting status is displayed
- **THEN** "In Progress" SHALL use `@string/meeting_status_in_progress`
- **THEN** duration format "1 hr" SHALL use `@string/meeting_duration_hour`
- **THEN** recurrence "Weekly" SHALL use `@string/meeting_recurrence_weekly`

## Requirement: Profile Strings

All UI text in Profile SHALL use string resources.

### Scenario: Profile header

- **WHEN** Profile is displayed
- **THEN** title "Profile" SHALL use `@string/profile_title`

### Scenario: Profile menu items

- **WHEN** profile menu is displayed
- **THEN** "Account Settings" SHALL use `@string/profile_account_settings`
- **THEN** "Meeting History" SHALL use `@string/profile_meeting_history`
- **THEN** "Help & Support" SHALL use `@string/profile_help_support`
- **THEN** "Log Out" SHALL use `@string/profile_log_out`

## Requirement: Settings Strings

All UI text in Settings SHALL use string resources.

### Scenario: Settings screen strings

- **WHEN** Settings is displayed
- **THEN** title "Settings" SHALL use `@string/settings_title`
- **THEN** language option SHALL use `@string/settings_language`
- **THEN** about option SHALL use `@string/settings_about`

## Requirement: Bottom Navigation Strings

Bottom navigation labels SHALL use string resources.

### Scenario: Bottom nav labels

- **WHEN** `bottom_nav_menu.xml` is loaded
- **THEN** Home tab SHALL use `@string/nav_home`
- **THEN** Calendar tab SHALL use `@string/nav_calendar`
- **THEN** Profile tab SHALL use `@string/nav_profile`

## Requirement: VideoCall Strings

All UI text in VideoCall flow SHALL use string resources.

### Scenario: PreJoin screen strings

- **WHEN** PreJoinFragment is displayed
- **THEN** meeting code label SHALL use `@string/prejoin_meeting_code`
- **THEN** display name label SHALL use `@string/prejoin_display_name`
- **THEN** join button SHALL use `@string/prejoin_join_button`
- **THEN** password label, password hint, password helper text,
  password-required error, meeting-not-found error, and checking-state text
  SHALL use string resources
- **THEN** waiting-for-approval, denied, expired, and connection failure
  messages SHALL use string resources

### Scenario: ActiveCall strings

- **WHEN** ActiveCallFragment is displayed
- **THEN** end call button SHALL use `@string/call_end`
- **THEN** participants button SHALL use `@string/call_participants`
- **THEN** chat button SHALL use `@string/call_chat`
- **THEN** connection quality labels, participant tile labels, screen-share
  placeholder text, and unread chat badge content SHALL use string resources

## Requirement: Empty State Strings

Empty states SHALL use string resources.

### Scenario: Dashboard empty state strings

- **WHEN** Dashboard empty state is displayed
- **THEN** message SHALL use `@string/empty_no_upcoming_meetings`
- **THEN** CTA button SHALL use `@string/empty_schedule_meeting`

### Scenario: Calendar empty state strings

- **WHEN** Calendar empty state is displayed
- **THEN** message SHALL use `@string/empty_no_meetings_today`

## Requirement: Accessibility Strings

Content descriptions SHALL use string resources.

### Scenario: Icon content descriptions

- **WHEN** any icon needs content description
- **THEN** it SHALL use one of:
    - `@string/cd_avatar` for profile pictures
    - `@string/cd_settings` for settings icon
    - `@string/cd_search` for search icon
    - `@string/cd_previous_month` for calendar nav
    - `@string/cd_next_month` for calendar nav
    - `@string/cd_new_meeting` for new meeting action
    - `@string/cd_join_meeting` for join meeting action
    - `@string/cd_schedule_meeting` for schedule action
    - `@string/cd_mute_mic` / `@string/cd_unmute_mic` for mic toggle
    - `@string/cd_enable_camera` / `@string/cd_disable_camera` for camera toggle
    - `@string/cd_end_call` for end call button
    - dedicated string resources for connection quality, self-view preview,
      screen-share placeholder, camera flip, pin participant, and raise-hand
      actions when those controls are present

### Scenario: Video tile status descriptions are accessible

- **WHEN** participant video tiles display mic-muted, camera-off, or
  active-speaker state
- **THEN** those visual states SHALL have accessible text equivalents through
  content descriptions or announced labels sourced from string resources

## Requirement: Vietnamese Translations

All new strings SHALL have Vietnamese translations in `values-vi/strings.xml`.

### Scenario: Vietnamese translation parity

- **WHEN** a new string is added to `values/strings.xml`
- **THEN** a corresponding translation SHALL be added to `values-vi/strings.xml`

### Scenario: Video call additions are localized

- **WHEN** new strings are added for password-protected join, checking state,
  meeting lookup errors, waiting-room approval, denial, expiration, connection
  quality, call controls, or participant tile states
- **THEN** Vietnamese translations SHALL be provided for each of those strings

### Scenario: Sample Vietnamese translations

- **WHEN** Vietnamese locale is active
- **THEN** `@string/nav_home` SHALL display "Trang chủ"
- **THEN** `@string/nav_calendar` SHALL display "Lịch"
- **THEN** `@string/nav_profile` SHALL display "Cá nhân"
- **THEN** `@string/action_new_meeting` SHALL display "Cuộc họp mới"
- **THEN** `@string/action_join_meeting` SHALL display "Tham gia họp"
- **THEN** `@string/profile_log_out` SHALL display "Đăng xuất"

## Requirement: Toast Message Removal

All Toast debug messages SHALL be removed.

### Scenario: Remove debug toasts in DashboardActivity

- **WHEN** `DashboardFragment` is created from DashboardActivity
- **THEN** Toast messages like "Mở màn hình Tạo phòng họp nhanh" SHALL be
  removed
- **THEN** Toast messages like "Chuyển sang tab Lịch" SHALL be removed

### Scenario: Remove debug toasts in all Activities

- **WHEN** any Activity is converted to Fragment
- **THEN** all `Toast.makeText(...).show()` debug calls SHALL be removed
- **THEN** only meaningful user feedback (errors, confirmations) SHALL remain as
  Snackbars

### Scenario: Profile logout toast

- **WHEN** user logs out from ProfileFragment
- **THEN** the "Đã đăng xuất" toast MAY be kept or converted to Snackbar
- **THEN** it SHALL use `@string/profile_logged_out` if kept
