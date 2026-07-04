## MODIFIED Requirements

### Requirement: Navigation Graph Structure

The `nav_graph_main.xml` SHALL define all main app destinations with proper
actions.

#### Scenario: Dashboard to Schedule navigation

- **WHEN** user taps the "Schedule" card on Dashboard
- **THEN** system navigates via `action_dashboard_to_schedule`
- **THEN** `ScheduleFragment` is added to back stack

#### Scenario: Dashboard meeting creation menu to Schedule navigation

- **WHEN** user selects "Schedule Meeting" from the dashboard FAB popup menu
- **THEN** system navigates via `action_dashboard_to_schedule`
- **THEN** `ScheduleFragment` is added to back stack

#### Scenario: Dashboard to CreateMeeting navigation

- **WHEN** another in-app entry point explicitly requests the legacy create
  meeting screen
- **THEN** system navigates via `action_dashboard_to_createMeeting`
- **THEN** `CreateMeetingFragment` is added to back stack

#### Scenario: Dashboard to JoinMeeting navigation

- **WHEN** user taps "Join Meeting" card on Dashboard
- **THEN** system navigates via `action_dashboard_to_joinMeeting`
- **THEN** `JoinMeetingFragment` is added to back stack

#### Scenario: Profile to Settings navigation

- **WHEN** user taps Settings on Profile screen
- **THEN** system navigates via `action_profile_to_settings`
- **THEN** `SettingsFragment` is added to back stack

### Requirement: Activity to Fragment Conversion

Each converted Activity SHALL become a Fragment maintaining the same ViewModel
and UI logic.

#### Scenario: DashboardFragment structure

- **WHEN** `DashboardFragment` is created
- **THEN** it SHALL use `DashboardViewModel` via Hilt injection
- **THEN** it SHALL inflate `fragment_dashboard.xml` layout
- **THEN** it SHALL NOT contain direct API calls for meeting creation logic
- **THEN** it MAY launch `VideoCallActivity` in response to a ViewModel success
  event for instant meeting creation

#### Scenario: Fragment ViewModel injection

- **WHEN** a converted Fragment needs its ViewModel
- **THEN** it SHALL use `new ViewModelProvider(this).get(XxxViewModel.class)`
- **THEN** the ViewModel SHALL be scoped to the Fragment

#### Scenario: Fragment LiveData observation

- **WHEN** a Fragment observes LiveData from ViewModel
- **THEN** it SHALL use `getViewLifecycleOwner()` as the lifecycle owner
- **THEN** it SHALL NOT use `this` (the Fragment instance) as lifecycle owner
