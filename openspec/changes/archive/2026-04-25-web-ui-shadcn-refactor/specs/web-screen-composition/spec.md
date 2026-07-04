# ADDED Requirements

## Requirement: Large web screens SHALL be decomposed into feature folders without changing routes

The web frontend SHALL split the existing auth, home, and meeting room screens
into feature-oriented component folders under `src/components/auth`,
`src/components/home`, and `src/components/meeting`. Each feature folder SHALL
expose a route-facing container component plus focused subcomponents for the
documented UI sections, while route files keep their current paths and continue
to render the same end-user flows.

### Scenario: Auth route keeps existing behavior after decomposition

- **WHEN** localized login or register routes render the auth experience
- **THEN** route imports MAY point to the new auth folder structure, but the
  login/register flow, redirects, and localized UI behavior SHALL remain
  unchanged

### Scenario: Meeting route keeps existing composition after decomposition

- **WHEN** the localized meeting room route renders the meeting experience
- **THEN** the container, toolbar, sidebar, participant grid, and chat
  subcomponents SHALL compose the same user-visible meeting flow without
  changing route structure or navigation entry points

## Requirement: Screen refactors SHALL preserve localization and navigation contracts

All screen decomposition and shared component adoption performed by this change
SHALL preserve existing next-intl translation usage, localized routing, and
user-triggered navigation outcomes. Refactored components SHALL continue to
obtain user-visible strings through translation hooks rather than introducing
hardcoded replacements.

### Scenario: Localized routes remain valid

- **WHEN** users navigate between localized home, auth, workspace, green-room,
  and meeting pages
- **THEN** the same route paths and locale-aware rendering behavior SHALL
  continue to work after the refactor

### Scenario: User-visible strings remain localized

- **WHEN** refactored subcomponents render labels, headings, buttons, or status
  text
- **THEN** they SHALL continue to source user-visible copy through the existing
  next-intl translation flow

## Requirement: Mock and fixture content SHALL be externalized into typed modules

Fixture data currently embedded in green-room and meeting-room screen components
SHALL move into dedicated modules under `src/lib/mock-data` with explicit
TypeScript typing. Refactored components SHALL import this data instead of
defining inline arrays so component files remain focused on composition and
behavior.

### Scenario: Green-room attendees are loaded from mock-data modules

- **WHEN** the green-room screen renders attendee fixture content during
  development or fallback states
- **THEN** the attendee list SHALL come from a typed module in
  `src/lib/mock-data` instead of an array literal inside the component file

### Scenario: Meeting participants and messages are loaded from mock-data modules

- **WHEN** the meeting room renders participant tiles or initial chat fixture
  content
- **THEN** those datasets SHALL come from typed modules in `src/lib/mock-data`
  rather than inline definitions inside the meeting screen component
