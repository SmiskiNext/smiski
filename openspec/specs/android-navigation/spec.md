# Purpose

Define the Android main-app navigation architecture, destination structure, and
fragment-based flows for the single-activity shell.

# ADDED Requirements

## Requirement: Single Activity Architecture

The main app flow SHALL use a single `MainActivity` hosting a `NavHostFragment`
with `nav_graph_main.xml` as the navigation graph.

### Scenario: App launches to Dashboard

- **WHEN** user completes login successfully
- **THEN** system navigates to `MainActivity` with `DashboardFragment` as the
  start destination
- **THEN** `BottomNavigationView` is visible with Home tab selected

### Scenario: MainActivity replaces old Activities

- **WHEN** `MainActivity` is created
- **THEN** `DashboardActivity`, `CalendarActivity`, `ProfileActivity` files
  SHALL be deleted
- **THEN** `ScheduleActivity`, `CreateMeetingActivity`, `JoinMeetingActivity`
  files SHALL be deleted

## Requirement: Bottom Navigation Integration

The `BottomNavigationView` SHALL be integrated with Navigation Component using
`NavigationUI.setupWithNavController()`.

### Scenario: Tab switching preserves state

- **WHEN** user is on Dashboard tab with scrolled content
- **THEN** user taps Calendar tab
- **THEN** user taps Home tab again
- **THEN** Dashboard content SHALL be restored to previous scroll position

### Scenario: Tab destinations

- **WHEN** `BottomNavigationView` is displayed
- **THEN** it SHALL have exactly 3 tabs: Home, Calendar, Profile
- **THEN** each tab SHALL navigate to its corresponding Fragment

## Requirement: BottomNav Visibility Control

The `BottomNavigationView` SHALL be hidden when navigating to full-screen
destinations.

### Scenario: Navigate to Schedule hides BottomNav

- **WHEN** user taps "Schedule" action on Dashboard
- **THEN** `ScheduleFragment` is displayed full-screen
- **THEN** `BottomNavigationView` is hidden
- **THEN** Toolbar with back button is visible

### Scenario: Back from full-screen shows BottomNav

- **WHEN** user is on `ScheduleFragment` (full-screen)
- **THEN** user presses back button
- **THEN** `DashboardFragment` is displayed
- **THEN** `BottomNavigationView` is visible

### Scenario: Full-screen destinations list

- **WHEN** navigation destination is one of: `ScheduleFragment`,
  `CreateMeetingFragment`, `JoinMeetingFragment`, `SettingsFragment`
- **THEN** `BottomNavigationView` SHALL be hidden

## Requirement: Navigation Graph Structure

The `nav_graph_main.xml` SHALL define all main app destinations with proper
actions.

### Scenario: Dashboard to Schedule navigation

- **WHEN** user taps "Schedule" card on Dashboard
- **THEN** system navigates via `action_dashboard_to_schedule`
- **THEN** `ScheduleFragment` is added to back stack

### Scenario: Dashboard meeting creation menu to Schedule navigation

- **WHEN** user selects "Schedule Meeting" from the dashboard FAB popup menu
- **THEN** system navigates via `action_dashboard_to_schedule`
- **THEN** `ScheduleFragment` is added to back stack

### Scenario: Dashboard to CreateMeeting navigation

- **WHEN** another in-app entry point explicitly requests the legacy create
  meeting screen
- **THEN** system navigates via `action_dashboard_to_createMeeting`
- **THEN** `CreateMeetingFragment` is added to back stack

### Scenario: Dashboard to JoinMeeting navigation

- **WHEN** user taps "Join Meeting" card on Dashboard
- **THEN** system navigates via `action_dashboard_to_joinMeeting`
- **THEN** `JoinMeetingFragment` is added to back stack

### Scenario: Profile to Settings navigation

- **WHEN** user taps Settings on Profile screen
- **THEN** system navigates via `action_profile_to_settings`
- **THEN** `SettingsFragment` is added to back stack

## Requirement: Activity to Fragment Conversion

Each converted Activity SHALL become a Fragment maintaining the same ViewModel
and UI logic.

### Scenario: DashboardFragment structure

- **WHEN** `DashboardFragment` is created
- **THEN** it SHALL use `DashboardViewModel` via Hilt injection
- **THEN** it SHALL inflate `fragment_dashboard.xml` layout
- **THEN** it SHALL NOT contain direct API calls for meeting creation logic
- **THEN** it MAY launch `VideoCallActivity` in response to a ViewModel success
  event for instant meeting creation

### Scenario: Fragment ViewModel injection

- **WHEN** a converted Fragment needs its ViewModel
- **THEN** it SHALL use `new ViewModelProvider(this).get(XxxViewModel.class)`
- **THEN** the ViewModel SHALL be scoped to the Fragment

### Scenario: Fragment LiveData observation

- **WHEN** a Fragment observes LiveData from ViewModel
- **THEN** it SHALL use `getViewLifecycleOwner()` as the lifecycle owner
- **THEN** it SHALL NOT use `this` (the Fragment instance) as lifecycle owner

## Requirement: Login Success Navigation Update

The login success flow SHALL navigate to `MainActivity` instead of
`DashboardActivity`.

### Scenario: Login navigates to MainActivity

- **WHEN** user successfully logs in via `LoginFragment`
- **THEN** system creates Intent to `MainActivity.class`
- **THEN** Intent has flags `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`
- **THEN** system calls `startActivity(intent)`

## Requirement: Settings Fragment

A new `SettingsFragment` SHALL be created to replace the placeholder Settings
functionality.

### Scenario: Settings screen content

- **WHEN** `SettingsFragment` is displayed
- **THEN** it SHALL show: Language selector, Theme toggle (if applicable), About
  section
- **THEN** it SHALL have a toolbar with back button

### Scenario: Settings accessible from Profile

- **WHEN** user is on `ProfileFragment`
- **THEN** Settings button/icon SHALL be visible
- **THEN** tapping it navigates to `SettingsFragment`
