## MODIFIED Requirements

### Requirement: MeetingInvitationsCreated event carries issue link fields

The `meet.meeting.invitations.created` CloudEvent payload SHALL include the
meeting's Jira issue identifier fields so the `notification` service can
construct a Jira deep-link in invitation emails. The `MeetingInvitationsCreated`
proto message SHALL carry `issue_id` (field 14), `issue_key` (field 15), and
`project_key` (field 16) as string fields. The `meet` service's
`MeetingInvitationsCreatedEventProtoMapper` SHALL populate these fields from the
meeting aggregate's issue link. The `MeetingInvitationsCreatedEvent` domain
record SHALL carry the corresponding `issueId`, `issueKey`, and `projectKey`
fields.

#### Scenario: Invitation event includes issue_key for deep-link construction

- **WHEN** the `meet` service publishes a `meet.meeting.invitations.created`
  event for a meeting linked to a Jira issue
- **THEN** the event payload contains non-blank `issue_id`, `issue_key`, and
  `project_key` values matching the meeting's stored issue link

#### Scenario: Existing consumers unaffected by new fields

- **WHEN** a consumer that predates this change reads a
  `MeetingInvitationsCreated` message
- **THEN** it continues to function correctly because the new fields are
  append-only with proto3 empty-string defaults

### Requirement: MeetingInfoUpdated event carries short_code

The `meet.meeting.info.updated` CloudEvent payload SHALL include the meeting's
`short_code` so the `notification` service can display it in update emails. The
`MeetingInfoUpdated` proto message SHALL carry `short_code` as a top-level
string field (field 10). The `meet` service's
`MeetingInfoUpdatedEventProtoMapper` SHALL populate `short_code` from the
meeting aggregate. The `MeetingInfoUpdatedEvent` domain record SHALL carry a
`shortCode` field.

#### Scenario: Update event includes short_code for email display

- **WHEN** the `meet` service publishes a `meet.meeting.info.updated` event
- **THEN** the event payload contains a non-blank `short_code` matching the
  meeting's stored short code

### Requirement: Invitee response events carry issue link and short_code

The invitee response proto messages (`InviteeAccepted`, `InviteeDeclined`,
`InviteeTentative`) SHALL each carry `issue_id` (field 18), `issue_key` (field
19), `project_key` (field 20), and `short_code` (field 21) as string fields. The
`meet` service's corresponding proto mappers SHALL populate these fields. The
domain event records SHALL carry the corresponding fields.

#### Scenario: Accepted response event includes issue and short_code fields

- **WHEN** the `meet` service publishes a `meet.invitee.accepted` event
- **THEN** the payload contains `issue_id`, `issue_key`, `project_key`, and
  `short_code` matching the meeting's stored values

#### Scenario: Declined and tentative response events also carry issue fields

- **WHEN** the `meet` service publishes `meet.invitee.declined` or
  `meet.invitee.tentative` events
- **THEN** each payload contains the same `issue_id`, `issue_key`,
  `project_key`, and `short_code` fields

#### Scenario: Existing consumers unaffected by new fields

- **WHEN** a consumer that predates this change reads an invitee response
  message
- **THEN** it continues to function correctly because the new fields are
  append-only with proto3 empty-string defaults
