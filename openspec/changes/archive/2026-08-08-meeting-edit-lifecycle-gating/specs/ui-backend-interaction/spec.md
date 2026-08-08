## ADDED Requirements

### Requirement: Status-aware meeting edit surface on the project page

The project page SHALL present a single `Edit meeting` modal that lets the host
manage a meeting's information, settings, and invitees, with sections gated by
the meeting status. The modal SHALL always show and allow editing of `title`,
`description`, and the linked Jira issue. The modal SHALL show the start
date/time and time zone controls but SHALL disable them when the meeting status
is not `SCHEDULED`. The modal SHALL show the settings and invitee-management
sections when the meeting status is `SCHEDULED` or `RUNNING`, and SHALL hide
those sections when the status is `COMPLETED` or `CANCELED`. On save, the app
SHALL send only the operations whose values changed, using the existing update,
settings-replacement, and invitee add/remove backend operations.

#### Scenario: Editing a scheduled meeting exposes every section

- **WHEN** the host opens the edit modal for a `SCHEDULED` meeting
- **THEN** the modal shows editable title, description, issue link, start
  date/time, time zone, settings, and invitee management

#### Scenario: Editing a running meeting locks time fields but keeps settings and invitees

- **WHEN** the host opens the edit modal for a `RUNNING` meeting
- **THEN** the modal shows editable title, description, issue link, settings,
  and invitee management, and the start date/time and time zone controls are
  disabled

#### Scenario: Editing a completed or canceled meeting exposes only information

- **WHEN** the host opens the edit modal for a `COMPLETED` or `CANCELED` meeting
- **THEN** the modal shows editable title, description, and issue link, the time
  fields are disabled, and the settings and invitee sections are hidden

#### Scenario: Save sends only changed operations

- **WHEN** the host saves the edit modal after changing only the title
- **THEN** the app issues the meeting information update and does not call the
  settings-replacement or invitee endpoints

### Requirement: Editable issue link in the meeting edit surface

The meeting edit modal SHALL let the host change the Jira issue the meeting is
linked to, choosing from issues in the current project sourced from Jira. When
the host selects a different issue, the app SHALL send the newly selected
`issueId`, `issueKey`, and `projectKey` on the meeting information update. When
the host does not change the issue, the app SHALL preserve the meeting's current
issue link.

#### Scenario: Host changes the linked issue

- **WHEN** the host selects a different project issue in the edit modal and
  saves
- **THEN** the meeting information update carries the newly selected `issueId`,
  `issueKey`, and `projectKey`

#### Scenario: Unchanged issue link is preserved

- **WHEN** the host saves the edit modal without changing the linked issue
- **THEN** the meeting information update carries the meeting's existing issue
  link

### Requirement: Invitee management is add and remove only

The meeting edit surface SHALL let the host add new invitees and remove existing
invitees, and SHALL NOT provide any control to edit an existing invitee's
identity (`accountId`, `email`, or `displayName`). Adding and removing invitees
SHALL be available only when the meeting status is `SCHEDULED` or `RUNNING`.

#### Scenario: Host adds and removes invitees on an editable meeting

- **WHEN** the host adds one workspace user and removes one existing invitee on
  a `SCHEDULED` or `RUNNING` meeting and saves
- **THEN** the app calls the add-invitees operation for the added user and the
  remove-invitees operation for the removed invitee

#### Scenario: No control edits an existing invitee's identity

- **WHEN** the host views the invitee list in the edit surface
- **THEN** the surface offers add and remove controls only and exposes no field
  to change an existing invitee's account, email, or display name

### Requirement: Meeting action policy exposes edit across statuses without a separate settings action

The frontend meeting-action policy SHALL expose the `EDIT` action to the host in
every meeting status (`SCHEDULED`, `RUNNING`, `COMPLETED`, `CANCELED`), so that
settings and invitee management are reached through the unified edit surface on
the project page. The policy SHALL NOT expose a separate `SETTINGS` action on
the project page. The policy SHALL continue to expose `START`/`CANCEL` for
`SCHEDULED` and `JOIN`/`END` for `RUNNING` to the host as before, and SHALL
expose no host management actions to a non-host user.

#### Scenario: Host sees edit on a completed meeting

- **WHEN** the action policy is evaluated for a host on a `COMPLETED` meeting
- **THEN** the available actions include `EDIT` and the view actions, and do not
  include a separate `SETTINGS` action

#### Scenario: Settings reached through edit on a running meeting

- **WHEN** the action policy is evaluated for a host on a `RUNNING` meeting
- **THEN** the available actions include `EDIT` and `JOIN` and `END`, and do not
  include a separate `SETTINGS` action

#### Scenario: Non-host sees no management actions

- **WHEN** the action policy is evaluated for a non-host user on any status
- **THEN** the available actions include no `EDIT`, `SETTINGS`, `CANCEL`, or
  `END` action

### Requirement: Issue panel is limited to create, list, view, and join

The issue panel surface SHALL be limited to creating meetings (instant and
scheduled), listing the issue's meetings, viewing meeting detail and history,
and joining a `RUNNING` meeting. The issue panel SHALL NOT offer edit, cancel,
start, end, or settings actions.

#### Scenario: Issue panel offers view and join only for a running meeting

- **WHEN** the host views a `RUNNING` meeting in the issue panel
- **THEN** the available actions are limited to join and view detail/history,
  and no edit, cancel, end, or settings action is shown

#### Scenario: Issue panel offers view only for a scheduled meeting

- **WHEN** the host views a `SCHEDULED` meeting in the issue panel
- **THEN** the available actions are limited to view detail/history, and no
  edit, cancel, start, or settings action is shown

#### Scenario: Issue panel keeps meeting creation

- **WHEN** the host with edit permission views the issue panel
- **THEN** the panel offers the instant-meeting and schedule-meeting create
  actions
