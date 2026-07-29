## ADDED Requirements

### Requirement: App sources instant invitees from workspace users

The Forge app SHALL populate the instant-meeting invitee list from Jira site
(workspace) users rather than only project-assignable users. Each invitee the
app submits to `POST /api/1/meetings:instant` SHALL carry the `email`,
`accountId`, and `displayName` resolved from the selected workspace user, so the
request satisfies the endpoint's required invitee shape.

#### Scenario: Workspace-sourced invitees satisfy the endpoint contract

- **WHEN** the app creates an instant meeting with invitees selected from
  workspace users
- **THEN** each invitee in the request body has a non-blank `email`,
  `accountId`, and `displayName`

#### Scenario: Instant meeting without invitees omits the list

- **WHEN** the host creates an instant meeting without selecting invitees
- **THEN** the app sends the request with an empty or absent invitees array and
  the meeting is still created

### Requirement: App creates instant meetings from dashboard and Issue Panel

The Forge app SHALL let the host create an instant meeting from both the
project-page dashboard and the Issue Panel, using one shared instant-meeting
form. From the Issue Panel, the create action SHALL first apply the existing
active-meeting (host conflict) check and then open the shared form; it SHALL NOT
create the meeting without showing the form. Both entry points SHALL send the
same instant-create request contract to the backend.

#### Scenario: Create from the dashboard

- **WHEN** the host starts an instant meeting from the dashboard
- **THEN** the shared instant-meeting form is shown and, on submit, an
  instant-create request is sent to the backend

#### Scenario: Create from the Issue Panel opens the form

- **WHEN** the host triggers "Start instant" from the Issue Panel and no host
  conflict blocks it
- **THEN** the shared instant-meeting form opens prefilled for the current issue
  instead of creating a meeting immediately

#### Scenario: Host conflict still guards the Issue Panel entry

- **WHEN** the host triggers "Start instant" from the Issue Panel while already
  hosting a running meeting
- **THEN** the active-meeting warning is shown before the instant-meeting form
