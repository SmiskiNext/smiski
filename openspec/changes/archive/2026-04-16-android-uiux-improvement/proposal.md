# Why

The Android app currently uses a fragmented multi-Activity architecture where
Dashboard, Calendar, and Profile are separate Activities with duplicated
BottomNavigationView logic. Navigation between tabs causes full Activity
recreation, losing state and creating jarring transitions. Additionally, the UI
has inconsistent styling with hardcoded colors (#1877F2, #666666), system icons
(@android:drawable), no dark mode support, and incomplete i18n (only auth
screens are translated).

This change will modernize the app architecture and UI/UX to provide a cohesive,
accessible, and maintainable foundation before adding business logic.

## What Changes

- **Navigation Architecture**: Convert multi-Activity pattern to
  Single-Activity + Navigation Component
    - Create `MainActivity` with `NavHostFragment` + `BottomNavigationView`
    - Convert 7 Activities to Fragments (Dashboard, Calendar, Profile, Schedule,
      CreateMeeting, JoinMeeting + new Settings)
    - BottomNav hides on deep navigation (full-screen fragments)
- **VideoCall Architecture**: Prepare for LiveKit integration
    - Create `VideoCallActivity` as separate task (taskAffinity, singleInstance,
      configChanges for PiP)
    - Convert MeetingRoom, Participants, MeetingChat to Fragments/BottomSheets
    - Merge `JoinGuestActivity` into `PreJoinFragment` with isGuest flag
- **UI Redesign**: Full Material Design 3 compliance
    - Replace all hardcoded colors with theme attributes (?attr/colorPrimary,
      etc.)
    - Add 10 new M3 color tokens (primaryContainer, surfaceVariant, etc.)
    - Replace 12 system icons with Material Symbols
    - Add dark mode support (drawable-night variants, theme colors)
    - Fix accessibility (contentDescription, 48dp touch targets, semantic
      headings)
    - Design empty states for Dashboard and Calendar
- **i18n Completion**: Full EN/VI coverage
    - Move ~80-100 hardcoded UI strings to strings.xml
    - Update bottom_nav_menu.xml with @string/ references
    - Remove all Toast debug messages
- **DELETE**: JoinGuestActivity (merged into VideoCallActivity)
- **DELETE**: Old Activity files after conversion (DashboardActivity,
  CalendarActivity, ProfileActivity, etc.)

## Capabilities

### New Capabilities

- `android-navigation`: Single-Activity architecture with Navigation Component,
  BottomNavigationView integration, and fragment-based navigation graph
- `android-design-system`: Material Design 3 theming, color tokens, typography
  scale, icon system, and dark mode support
- `android-videocall-shell`: VideoCallActivity structure for LiveKit
  integration, PreJoinFragment, and call flow navigation (UI shell only, no
  LiveKit SDK yet)
- `android-i18n-main`: Internationalization for main app screens (Dashboard,
  Calendar, Profile, Settings, Meeting flows) in EN/VI

### Modified Capabilities

(none - this is a UI/UX improvement, no existing spec requirements are changing)

## Impact

**Code Changes:**

- `app/src/main/java/io/github/phunguy65/zms/presentation/` — Major
  restructuring (Activities → Fragments)
- `app/src/main/res/layout/` — Rename activity*\*.xml → fragment*\*.xml,
  redesign layouts
- `app/src/main/res/values/` — colors.xml, themes.xml, dimens.xml, strings.xml
  updates
- `app/src/main/res/values-night/` — Dark mode theme additions
- `app/src/main/res/drawable/` — New Material icons, drawable-night variants
- `app/src/main/res/navigation/` — New nav_graph_main.xml, nav_graph_call.xml
- `app/src/main/AndroidManifest.xml` — Activity declarations, permissions

**Files to Delete:**

- DashboardActivity.java, CalendarActivity.java, ProfileActivity.java
- ScheduleActivity.java, CreateMeetingActivity.java, JoinMeetingActivity.java
- JoinGuestActivity.java, MeetingRoomActivity.java, ParticipantsActivity.java,
  MeetingChatActivity.java
- Corresponding activity\_\*.xml layout files

**Dependencies:**

- No new library dependencies (using existing Navigation Component, Material 3)
- New permissions: CAMERA, RECORD_AUDIO, MODIFY_AUDIO_SETTINGS (for VideoCall)

**Not Affected:**

- Auth flow (AuthActivity + nav_graph_auth.xml) — already uses Navigation
  Component
- Domain layer (models, repositories, use cases)
- Data layer (API, DTOs, mappers)
- Business logic (out of scope)
