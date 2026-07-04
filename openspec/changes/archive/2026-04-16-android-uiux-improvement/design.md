# Context

The Android app (`frontends/android-app`) is a video meeting application built
with Java + XML, MVVM + Clean Architecture, and Hilt DI. Currently it uses a
fragmented multi-Activity pattern where each main section (Dashboard, Calendar,
Profile) is a separate Activity with duplicated BottomNavigationView setup and
navigation logic.

**Current State:**

- 13 Activities in AndroidManifest (Splash, Welcome, Auth, JoinGuest, Dashboard,
  Calendar, Profile, Schedule, CreateMeeting, JoinMeeting, MeetingRoom,
  Participants, MeetingChat)
- Auth flow already uses Navigation Component (AuthActivity +
  nav_graph_auth.xml + Fragments)
- UI has hardcoded colors, system icons, no dark mode, incomplete i18n
- Tab navigation uses `startActivity()` + `finish()` — loses state, jarring
  transitions

**Constraints:**

- Java only (no Kotlin/Compose migration)
- XML layouts only
- Must work with existing Hilt DI setup
- No backward compatibility requirements
- LiveKit integration coming later (this change prepares the shell)

## Goals / Non-Goals

**Goals:**

- Single-Activity architecture for main app flow (MainActivity + Fragments)
- Consistent bottom navigation with state preservation
- Separate VideoCallActivity for video call flow (LiveKit-ready)
- Full Material Design 3 theming with dark mode support
- Complete EN/VI internationalization for all screens
- Accessibility compliance (touch targets, content descriptions)

**Non-Goals:**

- Business logic implementation
- API integration or data fetching
- LiveKit SDK integration (only preparing the shell)
- Error state handling (deferred)
- Automated testing
- Kotlin/Compose migration

## Decisions

### D1: Single-Activity + Navigation Component for Main Flow

**Decision:** Create `MainActivity` hosting a `NavHostFragment` with
`nav_graph_main.xml` containing Dashboard, Calendar, Profile, Schedule,
CreateMeeting, JoinMeeting, and Settings as fragment destinations.

**Alternatives Considered:**

- Keep multi-Activity pattern → Rejected: Duplicate code, no state preservation,
  poor UX
- Migrate to Jetpack Compose Navigation → Rejected: Out of scope, requires full
  UI rewrite

**Rationale:** Navigation Component is already used for auth flow, provides back
stack management, fragment state restoration, and integrates with
BottomNavigationView via `NavigationUI.setupWithNavController()`.

### D2: Separate VideoCallActivity for Video Calls

**Decision:** Create `VideoCallActivity` as a separate Android task with:

- `android:taskAffinity="io.github.phunguy65.zms.videocall"`
- `android:launchMode="singleInstance"`
- `android:configChanges="orientation|screenSize|smallestScreenSize|keyboardHidden"`
- `android:supportsPictureInPicture="true"`

**Alternatives Considered:**

- Keep video call in MainActivity nav graph → Rejected: Video calls need
  isolated lifecycle, PiP support, and separate back stack
- Keep as multiple Activities → Rejected: Fragments share ViewModel, easier
  state management

**Rationale:** LiveKit requires stable Room connection across configuration
changes. Separate task allows PiP mode, isolated back navigation (Back closes
call, not app), and appears as separate Recents card.

### D3: Merge JoinGuestActivity into PreJoinFragment

**Decision:** Delete `JoinGuestActivity`. Create `PreJoinFragment` inside
`VideoCallActivity` that handles both guest and authenticated users via
`isGuest` boolean flag.

**Alternatives Considered:**

- Keep JoinGuestActivity separate → Rejected: Duplicate pre-join UI, harder to
  maintain

**Rationale:** Both flows need same pre-join experience (camera preview, mic
toggle). Guest only adds display name field. Single fragment with conditional UI
is cleaner.

**Entry Points:**

- `WelcomeActivity` → "Join as Guest" → `VideoCallActivity` with `isGuest=true`
- `MainActivity` → "Join Meeting" → `VideoCallActivity` with `isGuest=false`

### D4: BottomNav Hidden on Deep Navigation

**Decision:** When navigating to full-screen destinations (Schedule,
CreateMeeting, JoinMeeting, Settings), hide the BottomNavigationView. Show
toolbar with back button instead.

**Implementation:**

```java
navController.addOnDestinationChangedListener((controller, dest, args) -> {
    boolean showBottomNav = dest.getId() == R.id.dashboardFragment
                         || dest.getId() == R.id.calendarFragment
                         || dest.getId() == R.id.profileFragment;
    bottomNav.setVisibility(showBottomNav ? View.VISIBLE : View.GONE);
});
```

**Rationale:** Full-screen destinations need maximum space and clear exit path
via back button.

### D5: Theme-Based Color System

**Decision:** Replace all hardcoded colors with Material 3 theme attributes:

| Hardcoded              | Theme Attribute               |
| ---------------------- | ----------------------------- |
| `#1877F2`              | `?attr/colorPrimary`          |
| `#666666`              | `?attr/colorOnSurfaceVariant` |
| `#F8F9FA`              | `?attr/colorSurfaceVariant`   |
| `#E0E0E0`              | `?attr/colorOutline`          |
| `@android:color/white` | `?attr/colorSurface`          |
| `@android:color/black` | `?attr/colorOnSurface`        |

**New M3 Tokens to Add:**

- `colorPrimaryContainer` / `colorOnPrimaryContainer`
- `colorSurfaceVariant` / `colorOnSurfaceVariant`
- `colorOutlineVariant`
- `colorErrorContainer`
- `colorSuccess` / `colorSuccessContainer` (semantic)

**Rationale:** Theme attributes enable dark mode support and consistent styling
across the app.

### D6: Material Symbols for Icons

**Decision:** Replace 12 system icons (`@android:drawable/ic_menu_*`) with
Material Symbols from Google Fonts.

**Icon Mapping:** | Current | Material Symbol | |---------|-----------------| |
`ic_menu_myplaces` | `home` | | `ic_menu_today` | `calendar_today` | |
`ic_menu_my_calendar` | `person` | | `ic_menu_preferences` | `settings` | |
`ic_menu_add` | `add` | | `ic_menu_search` | `search` | |
`ic_media_previous/next` | `chevron_left/right` | | `ic_menu_manage` |
`manage_accounts` | | `ic_menu_recent_history` | `history` | | `ic_menu_help` |
`help_outline` | | `ic_lock_power_off` | `logout` |

**Rationale:** System icons are inconsistent across OEMs/versions. Material
Symbols provide consistent, high-quality icons with outlined/filled variants for
state changes.

### D7: Fragment LiveData Observers Use getViewLifecycleOwner()

**Decision:** All Fragments observe LiveData using `getViewLifecycleOwner()`
instead of `this`.

```java
// Correct
viewModel.getData().observe(getViewLifecycleOwner(), data -> { ... });

// Incorrect (memory leak risk)
viewModel.getData().observe(this, data -> { ... });
```

**Rationale:** Using `this` (Fragment) as lifecycle owner can cause observers to
survive view destruction, leading to memory leaks and crashes when updating
destroyed views.

## Risks / Trade-offs

### R1: Large i18n Scope

**Risk:** ~80-100 new string keys needed (more than initially estimated)
**Mitigation:** Create all strings in Phase 5 before any UI changes to avoid
compile errors. Use grep to identify all hardcoded strings upfront.

### R2: Drawable Dark Mode Variants

**Risk:** 6-8 drawables hardcode light colors (bg_circle_white, bg_rounded_gray,
etc.) **Mitigation:** Either create drawable-night variants OR refactor
drawables to use `?attr/` colors where possible.

### R3: MeetingRoomViewModel Uses android.R.color

**Risk:** Java code uses `android.R.color.holo_blue_light` which doesn't adapt
to dark mode **Mitigation:** Replace with theme color resolution:
`MaterialColors.getColor(view, R.attr.colorPrimary)`

### R4: DatePickerDialog/TimePickerDialog in Fragments

**Risk:** `ScheduleActivity` uses `DatePickerDialog(this, ...)` — Fragment
conversion requires context change **Mitigation:** Use `requireContext()` or
`requireActivity()` in Fragment

### R5: Compile Blockers During Migration

**Risk:** Deleting Activities while other code references them causes compile
errors **Mitigation:** Update all references BEFORE deleting:

- `WelcomeActivity` → update `JoinGuestActivity.class` to
  `VideoCallActivity.class`
- `LoginFragment` → update `DashboardActivity.class` to `MainActivity.class`
- `CreateMeetingActivity` → remove manual back navigation (NavController handles
  it)

### R6: Missing Camera/Mic Permissions

**Risk:** VideoCall screens need camera/mic but Manifest only has INTERNET
permission **Mitigation:** Add permissions to Manifest + implement runtime
permission request in PreJoinFragment

## Architecture Diagrams

### Navigation Flow

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           APP NAVIGATION FLOW                                │
└──────────────────────────────────────────────────────────────────────────────┘

                         SplashActivity
                              │
                              ▼
                        WelcomeActivity
                              │
         ┌────────────────────┼────────────────────┐
         ▼                    ▼                    ▼
   AuthActivity          MainActivity        VideoCallActivity
   (nav_graph_auth)      (nav_graph_main)    (nav_graph_call)
         │                    │                    │
    ┌────┴────┐          ┌────┴────┐          ┌────┴────┐
    │ Login   │          │Dashboard│          │PreJoin  │
    │Register │◄─────────│Calendar │─────────►│Active   │
    │ForgotPwd│ success  │Profile  │ join mtg │Chat     │
    └─────────┘          │Schedule │          │Particip.│
                         │CreateMtg│          └─────────┘
                         │JoinMtg  │
                         │Settings │
                         └─────────┘
```

### MainActivity Fragment Structure

```
┌─────────────────────────────────────────────────────────────────┐
│                      MainActivity                                │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    NavHostFragment                         │  │
│  │                   nav_graph_main.xml                       │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │                                                     │  │  │
│  │  │  Tab Destinations (BottomNav visible):              │  │  │
│  │  │  • DashboardFragment (startDestination)             │  │  │
│  │  │  • CalendarFragment                                 │  │  │
│  │  │  • ProfileFragment                                  │  │  │
│  │  │                                                     │  │  │
│  │  │  Full-Screen Destinations (BottomNav hidden):       │  │  │
│  │  │  • ScheduleFragment                                 │  │  │
│  │  │  • CreateMeetingFragment                            │  │  │
│  │  │  • JoinMeetingFragment                              │  │  │
│  │  │  • SettingsFragment                                 │  │  │
│  │  │                                                     │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              BottomNavigationView                          │  │
│  │           [Home]    [Calendar]    [Profile]                │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### VideoCallActivity Fragment Structure

```
┌─────────────────────────────────────────────────────────────────┐
│                    VideoCallActivity                             │
│            (Separate task, PiP enabled)                          │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    NavHostFragment                         │  │
│  │                   nav_graph_call.xml                       │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │  PreJoinFragment (startDestination)                 │  │  │
│  │  │  • Camera preview                                   │  │  │
│  │  │  • Meeting code input                               │  │  │
│  │  │  • Display name (if isGuest=true)                   │  │  │
│  │  │  • Mic/Camera toggles                               │  │  │
│  │  │  • Join button                                      │  │  │
│  │  │                    │                                │  │  │
│  │  │                    ▼                                │  │  │
│  │  │  ActiveCallFragment                                 │  │  │
│  │  │  • Video grid                                       │  │  │
│  │  │  • Call controls overlay                            │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │                                                            │  │
│  │  BottomSheets (shown over ActiveCallFragment):             │  │
│  │  • ParticipantsBottomSheet                                 │  │
│  │  • MeetingChatBottomSheet                                  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  CallViewModel (ActivityScoped, shared across all fragments)     │
└─────────────────────────────────────────────────────────────────┘
```
