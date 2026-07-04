# Tasks

## 1. Foundation — Design System

- [x] 1.1 Update `values/colors.xml` — add 10 new M3 color tokens
      (primaryContainer, surfaceVariant, outlineVariant, errorContainer,
      success, successContainer for both light and dark)
- [x] 1.2 Update `values/themes.xml` — map new color tokens to theme attributes
- [x] 1.3 Update `values-night/themes.xml` — map dark color tokens to theme
      attributes
- [x] 1.4 Update `values/dimens.xml` — add corner_radius_sm/xl,
      icon_size_sm/md/lg, avatar_size_sm/md/lg, quick_action_size
- [x] 1.5 Create `res/color/bottom_nav_color.xml` — state list (checked →
      colorPrimary, default → colorOnSurfaceVariant)
- [x] 1.6 Download Material Symbols icons (12 icons) and import as vector
      drawables: home, calendar_today, person, settings, add, search,
      chevron_left, chevron_right, manage_accounts, history, help_outline,
      logout, videocam
- [x] 1.7 Create icon selectors for BottomNav: `ic_home_selector.xml`,
      `ic_calendar_selector.xml`, `ic_person_selector.xml` (outlined/filled
      states)
- [x] 1.8 Update `res/menu/bottom_nav_menu.xml` — use new icon selectors and
      @string/ for titles

## 2. Foundation — i18n Strings

- [x] 2.1 Add navigation strings to `values/strings.xml`: nav_home,
      nav_calendar, nav_profile
- [x] 2.2 Add dashboard strings: dashboard_title, action_new_meeting,
      action_new_meeting_subtitle, action_join_meeting,
      action_join_meeting_subtitle, action_schedule, action_schedule_subtitle,
      section_upcoming_meetings, action_see_all, action_join, action_wait
- [x] 2.3 Add calendar strings: calendar_title, meeting_status_in_progress,
      meeting_duration_hour, meeting_recurrence_weekly, weekday_abbreviations
      array
- [x] 2.4 Add profile strings: profile_title, profile_account_settings,
      profile_meeting_history, profile_help_support, profile_log_out,
      profile_logged_out
- [x] 2.5 Add settings strings: settings_title, settings_language,
      settings_about
- [x] 2.6 Add videocall strings: prejoin_meeting_code, prejoin_display_name,
      prejoin_join_button, call_end, call_participants, call_chat
- [x] 2.7 Add empty state strings: empty_no_upcoming_meetings,
      empty_schedule_meeting, empty_no_meetings_today
- [x] 2.8 Add accessibility strings: cd_avatar, cd_settings, cd_search,
      cd_previous_month, cd_next_month, cd_new_meeting, cd_join_meeting,
      cd_schedule_meeting, cd_mute_mic, cd_unmute_mic, cd_enable_camera,
      cd_disable_camera, cd_end_call
- [x] 2.9 Add all corresponding Vietnamese translations to
      `values-vi/strings.xml`

## 3. Navigation Architecture — MainActivity

- [x] 3.1 Create `MainActivity.java` — @AndroidEntryPoint, setContentView, setup
      NavHostFragment + BottomNavigationView
- [x] 3.2 Create `activity_main.xml` — ConstraintLayout with NavHostFragment
      (nav_graph_main) + BottomNavigationView (bottom_nav_menu)
- [x] 3.3 Create `res/navigation/nav_graph_main.xml` — define destinations:
      dashboardFragment (start), calendarFragment, profileFragment,
      scheduleFragment, createMeetingFragment, joinMeetingFragment,
      settingsFragment
- [x] 3.4 Implement BottomNav visibility control in MainActivity — hide for
      full-screen destinations (schedule, createMeeting, joinMeeting, settings)
- [x] 3.5 Setup NavigationUI.setupWithNavController() for BottomNav +
      NavController integration

## 4. Navigation Architecture — Fragment Conversions

- [x] 4.1 Create `DashboardFragment.java` from DashboardActivity — use
      ViewModelProvider, inflate fragment_dashboard, remove startActivity()
      calls, use NavController for navigation
- [x] 4.2 Create `fragment_dashboard.xml` from activity_dashboard.xml — remove
      BottomNavigationView (now in MainActivity)
- [x] 4.3 Create `CalendarFragment.java` from CalendarActivity — use
      getViewLifecycleOwner() for LiveData observers
- [x] 4.4 Create `fragment_calendar.xml` from activity_calendar.xml — remove
      BottomNavigationView
- [x] 4.5 Create `ProfileFragment.java` from ProfileActivity — use NavController
      for settings navigation
- [x] 4.6 Create `fragment_profile.xml` from activity_profile.xml — remove
      BottomNavigationView
- [x] 4.7 Create `ScheduleFragment.java` from ScheduleActivity — use
      requireContext() for DatePickerDialog/TimePickerDialog
- [x] 4.8 Create `fragment_schedule.xml` from activity_schedule.xml — add
      toolbar with back button
- [x] 4.9 Create `CreateMeetingFragment.java` from CreateMeetingActivity —
      remove manual back navigation (use NavController.popBackStack)
- [x] 4.10 Create `fragment_create_meeting.xml` from activity_new_meeting.xml —
      add toolbar with back button
- [x] 4.11 Create `JoinMeetingFragment.java` from JoinMeetingActivity
- [x] 4.12 Create `fragment_join_meeting.xml` from activity_join_meeting.xml —
      add toolbar with back button
- [x] 4.13 Create `SettingsFragment.java` — new fragment with language selector,
      about section
- [x] 4.14 Create `fragment_settings.xml` — toolbar with back button, settings
      menu items

## 5. Navigation Architecture — Entry Point Updates

- [x] 5.1 Update `LoginFragment.java` — change DashboardActivity.class to
      MainActivity.class in login success navigation
- [x] 5.2 Update `RegisterFragment.java` — if it navigates to Dashboard, update
      to MainActivity.class (NOT NEEDED - navigates to login)
- [x] 5.3 Update `AndroidManifest.xml` — add MainActivity, update exported flags

## 6. VideoCall Architecture

- [x] 6.1 Create `VideoCallActivity.java` — @AndroidEntryPoint, setup
      NavHostFragment with nav_graph_call, handle isGuest intent extra
- [x] 6.2 Create `activity_video_call.xml` — NavHostFragment container
- [x] 6.3 Create `res/navigation/nav_graph_call.xml` — destinations:
      preJoinFragment (start), activeCallFragment with popUpTo action
- [x] 6.4 Create `CallViewModel.java` — @HiltViewModel, LiveData for
      isMicEnabled, isCameraEnabled, meetingCode, displayName
- [x] 6.5 Create `PreJoinFragment.java` — camera preview placeholder, meeting
      code input, conditional display name (isGuest), mic/camera toggles, join
      button with validation
- [x] 6.6 Create `fragment_prejoin.xml` — layout matching PreJoinFragment
      requirements
- [x] 6.7 Create `ActiveCallFragment.java` from MeetingRoomActivity — video grid
      placeholder, call controls, participants/chat buttons
- [x] 6.8 Create `fragment_active_call.xml` from activity_meeting_room.xml
- [x] 6.9 Create `ParticipantsBottomSheet.java` from ParticipantsActivity —
      BottomSheetDialogFragment with participant list
- [x] 6.10 Create `layout_participants_sheet.xml` from activity_participants.xml
- [x] 6.11 Create `MeetingChatBottomSheet.java` from MeetingChatActivity —
      BottomSheetDialogFragment, remove cardMiniVideo
- [x] 6.12 Create `layout_meeting_chat_sheet.xml` from activity_meeting_chat.xml
      — remove mini video view
- [x] 6.13 Update `WelcomeActivity.java` — change JoinGuestActivity.class to
      VideoCallActivity.class with isGuest=true
- [x] 6.14 Update `AndroidManifest.xml` — add VideoCallActivity with
      taskAffinity, singleInstance, configChanges, PiP flags
- [x] 6.15 Add camera/mic permissions to `AndroidManifest.xml` — CAMERA,
      RECORD_AUDIO, MODIFY_AUDIO_SETTINGS
- [x] 6.16 Implement runtime permission request in PreJoinFragment

## 7. UI Redesign — Replace Hardcoded Colors

- [x] 7.1 Update `fragment_dashboard.xml` — replace all hardcoded colors
      (#1877F2, #666666, #F8F9FA, @android:color/black/white) with ?attr/
      references
- [x] 7.2 Update `fragment_calendar.xml` — replace hardcoded colors with theme
      attributes
- [x] 7.3 Update `fragment_profile.xml` — replace hardcoded colors (#666666,
      #EAEAEA, #4A5568, #A0AEC0, #E53E3E) with theme attributes
- [x] 7.4 Update `fragment_schedule.xml` — replace hardcoded colors
- [x] 7.5 Update `fragment_create_meeting.xml` — replace hardcoded colors
- [x] 7.6 Update `fragment_join_meeting.xml` — replace hardcoded colors
- [x] 7.7 Update `fragment_prejoin.xml` — use theme colors
- [x] 7.8 Update `fragment_active_call.xml` — replace android.R.color references
      with theme colors
- [x] 7.9 Replace hardcoded colors in ActiveCallFragment.java/CallViewModel —
      use MaterialColors.getColor() instead of android.R.color

## 8. UI Redesign — Replace System Icons

- [x] 8.1 Update `fragment_dashboard.xml` — replace @android:drawable icons with
      Material Symbols
- [x] 8.2 Update `fragment_calendar.xml` — replace @android:drawable icons
- [x] 8.3 Update `fragment_profile.xml` — replace @android:drawable icons
- [x] 8.4 Update all other fragment layouts — replace remaining
      @android:drawable icons

## 9. UI Redesign — Dark Mode Support

- [x] 9.1 Create `drawable-night/bg_circle_white.xml` — dark mode variant
- [x] 9.2 Create `drawable-night/bg_rounded_gray.xml` — dark mode variant
- [x] 9.3 Create `drawable-night/bg_circle_blue.xml` — dark mode variant or
      refactor to use ?attr/
- [x] 9.4 Create `drawable-night/bg_leave_button.xml` — dark mode variant
- [x] 9.5 Review and update remaining drawables that hardcode colors
- [ ] 9.6 Test app in dark mode — verify all screens render correctly

## 10. UI Redesign — Accessibility

- [x] 10.1 Add contentDescription to all ImageViews in fragment_dashboard.xml
- [x] 10.2 Add contentDescription to all ImageViews in fragment_calendar.xml
- [x] 10.3 Add contentDescription to all ImageViews in fragment_profile.xml
- [x] 10.4 Add contentDescription to all ImageViews in other fragments
- [x] 10.5 Fix touch targets — wrap settings icon (28dp) in 48dp clickable
      container
- [x] 10.6 Fix touch targets — wrap calendar nav arrows (32dp) in 48dp clickable
      containers
- [x] 10.7 Add accessibilityHeading="true" to section headers (Upcoming
      Meetings, etc.)

## 11. UI Redesign — Empty States

- [x] 11.1 Create `layout_empty_dashboard.xml` — illustration placeholder,
      message text, CTA button
- [x] 11.2 Create `layout_empty_calendar.xml` — illustration placeholder,
      message text
- [x] 11.3 Update DashboardFragment — show empty state when no upcoming meetings
- [x] 11.4 Update CalendarFragment — show empty state when no events for
      selected day

## 12. Cleanup — Remove Debug Toasts

- [x] 12.1 Remove Toast.makeText() calls from DashboardFragment (or converted
      code)
- [x] 12.2 Remove Toast.makeText() calls from CalendarFragment
- [x] 12.3 Remove Toast.makeText() calls from ProfileFragment — keep or convert
      logout toast to Snackbar
- [x] 12.4 Remove Toast.makeText() calls from all other fragments
- [x] 12.5 Update any remaining user feedback to use Snackbar with string
      resources

## 13. Cleanup — Delete Old Files

- [x] 13.1 Delete DashboardActivity.java, CalendarActivity.java,
      ProfileActivity.java
- [x] 13.2 Delete ScheduleActivity.java, CreateMeetingActivity.java,
      JoinMeetingActivity.java
- [x] 13.3 Delete JoinGuestActivity.java, JoinGuestViewModel.java
- [x] 13.4 Delete MeetingRoomActivity.java, ParticipantsActivity.java,
      MeetingChatActivity.java
- [x] 13.5 Delete old layout files: activity_dashboard.xml,
      activity_calendar.xml, activity_profile.xml, activity_schedule.xml,
      activity_new_meeting.xml, activity_join_meeting.xml,
      activity_join_guest.xml, activity_meeting_room.xml,
      activity_participants.xml, activity_meeting_chat.xml
- [x] 13.6 Update AndroidManifest.xml — remove deleted Activity declarations
- [x] 13.7 Update `app/codemap.md` — reflect new architecture

## 14. Verification

- [x] 14.1 Run `./gradlew assembleDebug` — verify no compile errors
- [x] 14.2 Run `./gradlew lint` — fix any critical lint issues
- [ ] 14.3 Manual test: Splash → Welcome → Login → MainActivity (Dashboard
      visible)
- [ ] 14.4 Manual test: Tab navigation (Home ↔ Calendar ↔ Profile) with state
      preservation
- [ ] 14.5 Manual test: Dashboard → Schedule (BottomNav hidden) → Back
      (BottomNav visible)
- [ ] 14.6 Manual test: Dashboard → New Meeting → Join button →
      VideoCallActivity
- [ ] 14.7 Manual test: Welcome → Join as Guest → VideoCallActivity (PreJoin
      with name field)
- [ ] 14.8 Manual test: ActiveCall → Participants sheet → dismiss
- [ ] 14.9 Manual test: ActiveCall → Chat sheet → dismiss
- [ ] 14.10 Manual test: End call → returns to previous screen
- [ ] 14.11 Manual test: Dark mode — all screens render correctly
- [ ] 14.12 Manual test: Vietnamese locale — all strings translated
